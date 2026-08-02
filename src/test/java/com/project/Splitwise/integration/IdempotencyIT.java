package com.project.Splitwise.integration;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.readmodel.repository.PoisonMessageRepository;
import com.project.Splitwise.repository.BalanceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the effectively-once claim by doing what Kafka actually does on retry: delivering
 * the same record more than once.
 *
 * <p>Events are published straight to the topic rather than through the HTTP API, because
 * the point is to control the {@code eventId} and replay it verbatim.
 */
class IdempotencyIT extends AbstractIntegrationTest {

    private static final AtomicLong GROUP_IDS = new AtomicLong(7_000);

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private BalanceRepository balanceRepository;
    @Autowired
    private PoisonMessageRepository poisonMessages;

    private static ExpenseCreatedEvent event(String eventId, long groupId) {
        return ExpenseCreatedEvent.builder()
                .eventId(eventId)
                .expenseId(1L)
                .groupId(groupId)
                .paidBy(1L)
                .amount(new BigDecimal("300.00"))
                .shares(List.of(
                        new ExpenseCreatedEvent.Share(1L, new BigDecimal("100.00")),
                        new ExpenseCreatedEvent.Share(2L, new BigDecimal("100.00")),
                        new ExpenseCreatedEvent.Share(3L, new BigDecimal("100.00"))))
                .build();
    }

    /**
     * Asserts the net balance, treating a not-yet-written row as a retryable failure rather
     * than an error. Awaitility retries {@link AssertionError} but propagates anything else,
     * so a null here would abort the await on the first poll instead of waiting for the
     * consumer to catch up.
     */
    private void assertNet(long groupId, long userId, String expected) {
        BigDecimal actual = balanceRepository.findByGroupIdAndUserId(groupId, userId)
                .map(Balance::getNetBalance)
                .orElse(null);
        assertNotNull(actual, () -> "no balance row yet for group " + groupId + " user " + userId);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("redelivering the identical event does not double-count the balance")
    void duplicateDeliveryIsIgnored() {
        long groupId = GROUP_IDS.incrementAndGet();
        String eventId = UUID.randomUUID().toString();
        String key = String.valueOf(groupId);

        kafkaTemplate.send("expense-created", key, event(eventId, groupId));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertNet(groupId, 1L, "200.00"));

        // Same eventId, same payload — exactly what an at-least-once broker replays.
        kafkaTemplate.send("expense-created", key, event(eventId, groupId));
        kafkaTemplate.send("expense-created", key, event(eventId, groupId));

        // Give the consumer time to actually process (and ignore) both duplicates before
        // asserting; otherwise this passes simply because nothing has been consumed yet.
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertNet(groupId, 1L, "200.00");
            assertNet(groupId, 2L, "-100.00");
            assertNet(groupId, 3L, "-100.00");
        });
    }

    @Test
    @DisplayName("distinct events for the same group accumulate rather than dedupe")
    void distinctEventsBothApply() {
        long groupId = GROUP_IDS.incrementAndGet();
        String key = String.valueOf(groupId);

        kafkaTemplate.send("expense-created", key, event(UUID.randomUUID().toString(), groupId));
        kafkaTemplate.send("expense-created", key, event(UUID.randomUUID().toString(), groupId));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertNet(groupId, 1L, "400.00"));
    }

    @Test
    @DisplayName("a structurally invalid event is rejected without corrupting balances")
    void poisonEventDoesNotStallOrCorrupt() {
        long groupId = GROUP_IDS.incrementAndGet();
        String key = String.valueOf(groupId);

        // Shares do not sum to the amount: non-retryable, so it routes to the DLT.
        ExpenseCreatedEvent poison = ExpenseCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .expenseId(2L)
                .groupId(groupId)
                .paidBy(1L)
                .amount(new BigDecimal("300.00"))
                .shares(List.of(new ExpenseCreatedEvent.Share(1L, new BigDecimal("10.00"))))
                .build();

        kafkaTemplate.send("expense-created", key, poison);

        // The consumer group must keep moving: a good event behind the poison one still lands.
        kafkaTemplate.send("expense-created", key, event(UUID.randomUUID().toString(), groupId));

        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertNet(groupId, 1L, "200.00"));
    }

    @Test
    @DisplayName("a poison payment is persisted too, not just a poison expense")
    void poisonPaymentIsCaptured() {
        long groupId = GROUP_IDS.incrementAndGet();
        String eventId = UUID.randomUUID().toString();

        // Self-transfer: structurally invalid, so the consumer rejects it as non-retryable.
        PaymentRecordedEvent poison = PaymentRecordedEvent.builder()
                .eventId(eventId)
                .paymentId(999L)
                .groupId(groupId)
                .fromUserId(1L)
                .toUserId(1L)
                .amount(new BigDecimal("50.00"))
                .build();

        kafkaTemplate.send("payment-recorded", String.valueOf(groupId), poison);

        // The DLT consumer previously listened only to expense-created.DLT, so a failed
        // payment vanished when the broker's retention expired.
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            boolean captured = poisonMessages.findAll().stream()
                    .anyMatch(p -> p.getPayload() != null && p.getPayload().contains(eventId));
            assertTrue(captured, "the poison payment should have been persisted");
        });
    }
}
