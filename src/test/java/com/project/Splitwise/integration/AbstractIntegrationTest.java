package com.project.Splitwise.integration;

import com.project.Splitwise.dto.AuthDtos;
import com.project.Splitwise.dto.GroupDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Boots the application against a real Postgres and a real Kafka broker.
 *
 * <p>Containers are started once for the whole JVM rather than per test class. The
 * alternative ({@code @Testcontainers} with {@code @Container}) tears down and recreates a
 * broker for every class, which dominates the runtime of the suite.
 *
 * <p>Requires a working Docker daemon. These are named {@code *IT} so failsafe runs them
 * under {@code mvn verify} and surefire leaves them alone during {@code mvn test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("splitwise_db")
                    .withUsername("splitwise")
                    .withPassword("splitwise");

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Tighten the relay loop so tests observe convergence quickly.
        registry.add("splitwise.outbox.poll-interval-ms", () -> "200");

        // Test-only signing key. Production has no default, so a real deployment cannot
        // accidentally inherit this one.
        registry.add("splitwise.jwt.secret",
                () -> "integration-test-signing-key-not-used-anywhere-else");

        // Let the management context take an ephemeral port too. Pinning it to the
        // configured 9090 would make the suite fail on any machine already using it, and
        // would collide with itself if two test JVMs ran at once.
        registry.add("management.server.port", () -> "0");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /** Actuator's port, assigned separately from the application's under RANDOM_PORT. */
    @LocalManagementPort
    private int managementPort;



    /**
     * A client pointed at the management context.
     *
     * <p>Actuator listens on its own port precisely so metrics can be scraped without a
     * bearer token, so tests reach it the same way Prometheus would rather than through the
     * authenticated application chain.
     */
    protected TestRestTemplate managementRestTemplate() {
        return new TestRestTemplate(new RestTemplateBuilder()
                .rootUri("http://localhost:" + managementPort));
    }

    /** Unique per registration so parallel classes cannot collide on the email index. */
    private static final AtomicLong USER_SEQ = new AtomicLong();

    /** A registered user together with the bearer token that authenticates them. */
    protected record TestUser(Long id, String email, String token) {
    }

    /**
     * Registers a new user and returns them with a live token.
     *
     * <p>Tests go through the real {@code /auth/register} endpoint rather than inserting
     * rows, so the token they carry is one the filter actually accepts.
     */
    protected TestUser registerUser() {
        String email = "user" + USER_SEQ.incrementAndGet() + "-" + UUID.randomUUID() + "@example.test";

        ResponseEntity<AuthDtos.AuthResponse> response = restTemplate.postForEntity(
                "/auth/register",
                new AuthDtos.RegisterRequest(email, "correct-horse-battery", "Test User"),
                AuthDtos.AuthResponse.class);

        AuthDtos.AuthResponse body = response.getBody();
        if (body == null || body.token() == null) {
            throw new IllegalStateException("Registration failed: " + response.getStatusCode());
        }
        return new TestUser(body.userId(), email, body.token());
    }

    protected HttpHeaders authHeaders(TestUser user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(user.token());
        return headers;
    }

    protected <T> HttpEntity<T> as(TestUser user, T body) {
        return new HttpEntity<>(body, authHeaders(user));
    }

    protected HttpEntity<Void> as(TestUser user) {
        return new HttpEntity<>(authHeaders(user));
    }

    /** Creates a group owned by {@code owner} and adds everyone else to it. */
    protected Long createGroup(TestUser owner, TestUser... others) {
        ResponseEntity<GroupDtos.GroupResponse> created = restTemplate.exchange(
                "/groups", HttpMethod.POST,
                as(owner, new GroupDtos.CreateGroupRequest("test group")),
                GroupDtos.GroupResponse.class);

        Long groupId = created.getBody().id();

        for (TestUser other : others) {
            restTemplate.exchange("/groups/" + groupId + "/members", HttpMethod.POST,
                    as(owner, new GroupDtos.AddMemberRequest(other.id())),
                    GroupDtos.GroupResponse.class);
        }
        return groupId;
    }
}
