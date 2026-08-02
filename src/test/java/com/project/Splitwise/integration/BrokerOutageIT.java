package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import com.project.Splitwise.repository.ExpenseRepository;
import com.project.Splitwise.repository.OutboxEventRepository;
import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The claim the whole architecture exists to support, tested rather than argued.
 *
 * <p>The README says a broker outage cannot lose a balance update, because the event is
 * committed to the database before anything tries to publish it. Every other test in this
 * suite runs against a healthy broker, so none of them can distinguish that design from one
 * that simply has not been unlucky yet. This one takes Kafka away mid-flight.
 *
 * <p>The outage is produced by pausing the container rather than stopping it. Stopping a
 * Testcontainers-managed broker destroys it, and the replacement comes back on a different
 * host port that the running application would never reconnect to — which would make the
 * recovery half of this test meaningless. Pausing suspends every process inside the container
 * while leaving its port bindings intact, so from the client's side connections hang, sends
 * go unacknowledged, and the broker later returns at the address it always had.
 */
class BrokerOutageIT extends AbstractIntegrationTest {

    @Autowired
    private GroupBalanceViewRepository balanceViews;
    @Autowired
    private OutboxEventRepository outboxEvents;
    @Autowired
    private ExpenseRepository expenses;

    private TestUser payer;
    private TestUser other;
    private Long groupId;

    @BeforeEach
    void setUpGroup() {
        payer = registerUser();
        other = registerUser();
        groupId = createGroup(payer, other);
    }

    /**
     * The broker is shared with every other test class in this JVM, so leaving it frozen
     * would fail everything that runs afterwards. This runs even when the test fails.
     */
    @AfterEach
    void ensureBrokerIsRunning() {
        if (isBrokerPaused()) {
            resumeBroker();
        }
    }

    private static DockerClient docker() {
        return DockerClientFactory.instance().client();
    }

    private static boolean isBrokerPaused() {
        return Boolean.TRUE.equals(
                docker().inspectContainerCmd(KAFKA.getContainerId()).exec().getState().getPaused());
    }

    /**
     * Freezes the broker's processes rather than stopping the container.
     *
     * <p>{@code stop} would destroy a Testcontainers-managed container, and the replacement
     * comes back on a different host port that the running application would never reconnect
     * to. {@code pause} leaves the container and its port bindings intact and suspends every
     * process inside it, which is what an unreachable or wedged broker looks like from the
     * client's side: connections hang, sends time out, and nothing is acknowledged.
     */
    private static void pauseBroker() {
        docker().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void resumeBroker() {
        docker().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private CreateExpenseRequest expense(String amount) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(List.of(payer.id(), other.id()));
        return req;
    }

    private ResponseEntity<String> postExpense(String amount) {
        return restTemplate.exchange("/expenses", HttpMethod.POST,
                as(payer, expense(amount)), String.class);
    }

    private BigDecimal projectedNet(Long userId) {
        return balanceViews.findByGroupId(groupId).stream()
                .filter(v -> v.getUserId().equals(userId))
                .map(v -> v.getNetBalance())
                .findFirst()
                .orElse(null);
    }

    /**
     * Asserts a projected balance, treating an absent row as a retryable failure.
     *
     * <p>Awaitility retries {@link AssertionError} and propagates everything else, so passing
     * a not-yet-written null into {@code compareTo} would abort the await on its first poll
     * rather than waiting for the projection to catch up.
     */
    private void assertNet(Long userId, String expected, String because) {
        BigDecimal actual = projectedNet(userId);
        assertNotNull(actual, () -> because + " (no projected balance yet for user " + userId + ")");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> because + " — expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("writes survive a broker outage and converge once it returns")
    void outageDoesNotLoseBalanceUpdates() {
        // Baseline: with a healthy broker, one expense converges. Establishes that the
        // pipeline is working before anything is broken, so a later failure is attributable.
        postExpense("100.00");
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                assertNet(payer.id(), "50.00", "baseline expense should have converged"));

        pauseBroker();

        // The write path must not depend on the broker being up. This is the whole point of
        // committing the event to the database instead of publishing it inline.
        assertEquals(HttpStatus.CREATED, postExpense("40.00").getStatusCode(),
                "writes must still be accepted while the broker is down");
        assertEquals(HttpStatus.CREATED, postExpense("60.00").getStatusCode());

        assertEquals(3, expenses.findByGroupId(groupId, PageRequest.of(0, 50)).getTotalElements(),
                "all three expenses should be durable in the database");

        // Staged and waiting: nothing was dropped, and nothing was published either.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(outboxEvents.countByPublishedAtIsNull() >= 2,
                        "events written during the outage should be sitting in the outbox"));

        // The assertion that stops this test passing for the wrong reason.
        //
        // Everything above holds trivially if the outage is shorter than one poll-and-send
        // cycle: the relay never tries, nothing fails, and the test proves only that Kafka
        // was briefly paused. Waiting for a recorded failure forces the outage to last long
        // enough for the relay to actually attempt publication and be refused, which is the
        // path the whole design exists to survive.
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> {
            long attempted = outboxEvents.findAll().stream()
                    .filter(e -> e.getPublishedAt() == null && e.getAttempts() > 0)
                    .count();
            assertTrue(attempted >= 1,
                    "the relay should have tried to publish and failed while the broker was down");
        });

        // And the read model has not moved, because no event reached a consumer.
        assertNet(payer.id(), "50.00", "balances must not change while events are unpublished");

        resumeBroker();

        // Recovery is unattended: the relay retries on its next poll, the consumers rejoin
        // their group, and the backlog drains without anyone replaying anything by hand.
        await().atMost(Duration.ofMinutes(3)).untilAsserted(() ->
                assertEquals(0, outboxEvents.countByPublishedAtIsNull(),
                        "the outbox should drain once the broker returns"));

        // 100 + 40 + 60 = 200 fronted, half of it the payer's own share, so they are owed 100.
        await().atMost(Duration.ofMinutes(2)).untilAsserted(() ->
                assertNet(payer.id(), "100.00",
                        "every expense written during the outage should be reflected"));

        BigDecimal total = balanceViews.findByGroupId(groupId).stream()
                .map(v -> v.getNetBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(BigDecimal.ZERO),
                "the group must still net to zero after an outage");
    }
}
