package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.service.BalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final BalanceService balanceService;

    public PaymentEventConsumer(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /**
     * Second writer to the balance rows, alongside {@link BalanceEventConsumer}.
     *
     * <p>Payments and expenses live on different topics, so their consumers can touch the
     * same group's rows concurrently. Partition-key serialisation no longer covers that on
     * its own, which is why {@code Balance} carries an {@code @Version}: a concurrent update
     * fails the optimistic lock, the record is retried by the error handler, and the second
     * attempt reads the committed value. The rolled-back attempt takes its
     * {@code processed_events} row with it, so the retry is not mistaken for a duplicate.
     */
    @KafkaListener(topics = "payment-recorded", groupId = "balance-service")
    public void consume(PaymentRecordedEvent event) {
        validate(event);
        balanceService.handlePayment(event);
        log.debug("Applied payment {} to group {}", event.getPaymentId(), event.getGroupId());
    }

    /** Structural rejection; {@link IllegalArgumentException} is non-retryable and routes to the DLT. */
    private void validate(PaymentRecordedEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("PaymentRecordedEvent has no eventId; cannot deduplicate");
        }
        if (event.getGroupId() == null) {
            throw new IllegalArgumentException("PaymentRecordedEvent has no groupId: " + event.getPaymentId());
        }
        if (event.getFromUserId() == null || event.getToUserId() == null) {
            throw new IllegalArgumentException("PaymentRecordedEvent needs both parties: " + event.getPaymentId());
        }
        if (event.getFromUserId().equals(event.getToUserId())) {
            throw new IllegalArgumentException(
                    "PaymentRecordedEvent is a self-transfer: " + event.getPaymentId());
        }
        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "PaymentRecordedEvent amount must be positive: " + event.getPaymentId());
        }
    }
}
