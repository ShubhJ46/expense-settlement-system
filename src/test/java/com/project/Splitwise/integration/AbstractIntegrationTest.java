package com.project.Splitwise.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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
    }
}
