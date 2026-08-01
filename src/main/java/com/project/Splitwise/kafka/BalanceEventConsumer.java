package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.service.BalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BalanceEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(BalanceEventConsumer.class);

    private final BalanceService balanceService;

    public BalanceEventConsumer(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /**
     * Offsets are committed by the container after this method returns normally
     * (ack-mode RECORD). Manual acknowledgment was removed: the previous version injected
     * an {@code Acknowledgment} without configuring a MANUAL ack-mode, so Spring passed
     * null and every message died on {@code ack.acknowledge()}.
     *
     * <p>Offset commit and the DB transaction are still two separate systems, so a crash
     * between them replays the record. {@link BalanceService#handleExpense} is idempotent,
     * which is what makes that replay harmless.
     */
    @KafkaListener(topics = "expense-created", groupId = "balance-service")
    public void consume(ExpenseCreatedEvent event) {
        validate(event);
        balanceService.handleExpense(event);
        log.debug("Applied expense {} to group {}", event.getExpenseId(), event.getGroupId());
    }

    /**
     * Structural rejection. These throw {@link IllegalArgumentException}, which is
     * registered as non-retryable, so a malformed event routes straight to the DLT
     * instead of burning three retry cycles on something that can never succeed.
     */
    private void validate(ExpenseCreatedEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("ExpenseCreatedEvent has no eventId; cannot deduplicate");
        }
        if (event.getGroupId() == null) {
            throw new IllegalArgumentException("ExpenseCreatedEvent has no groupId: " + event.getExpenseId());
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("ExpenseCreatedEvent amount must be positive: " + event.getExpenseId());
        }
        if (event.getShares() == null || event.getShares().isEmpty()) {
            throw new IllegalArgumentException("ExpenseCreatedEvent has no shares: " + event.getExpenseId());
        }

        BigDecimal shareTotal = event.getShares().stream()
                .map(ExpenseCreatedEvent.Share::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (shareTotal.compareTo(event.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "ExpenseCreatedEvent shares sum to " + shareTotal
                            + " but amount is " + event.getAmount()
                            + " (expenseId=" + event.getExpenseId() + ")");
        }
    }
}
