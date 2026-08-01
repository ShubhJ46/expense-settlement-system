package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.model.Payment;
import com.project.Splitwise.outbox.OutboxWriter;
import com.project.Splitwise.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    static final String PAYMENT_TOPIC = "payment-recorded";

    private final PaymentRepository paymentRepo;
    private final OutboxWriter outboxWriter;

    public PaymentService(PaymentRepository paymentRepo, OutboxWriter outboxWriter) {
        this.paymentRepo = paymentRepo;
        this.outboxWriter = outboxWriter;
    }

    /**
     * Records that a debt was paid and stages the balance mutation for the consumer.
     *
     * <p>This deliberately does not touch {@code balances} directly, even though it easily
     * could. Balance rows are mutated by exactly one writer — the Kafka consumer — and that
     * single-writer property is what lets the mutation be a plain read-modify-write. A
     * second writer reaching into the same rows from the HTTP thread would reintroduce the
     * lost-update window that the consumer design avoids.
     *
     * <p>Overpayment is allowed. Paying more than you owe simply flips the sign of your net
     * position, which is the same thing that happens in real life when someone rounds up.
     */
    @Transactional
    public Payment recordPayment(Long groupId, RecordPaymentRequest req) {
        validate(groupId, req);

        Payment payment = new Payment();
        payment.setGroupId(groupId);
        payment.setFromUserId(req.getFromUserId());
        payment.setToUserId(req.getToUserId());
        payment.setAmount(req.getAmount());
        payment.setNote(req.getNote());

        Payment saved = paymentRepo.save(payment);

        PaymentRecordedEvent event = PaymentRecordedEvent.builder()
                // Generated here, inside the transaction, so a relay retry republishes the
                // same id and the consumer deduplicates it instead of paying twice.
                .eventId(UUID.randomUUID().toString())
                .paymentId(saved.getId())
                .groupId(groupId)
                .fromUserId(saved.getFromUserId())
                .toUserId(saved.getToUserId())
                .amount(saved.getAmount())
                .build();

        outboxWriter.append(
                "Payment",
                String.valueOf(saved.getId()),
                PAYMENT_TOPIC,
                // Same key as expenses: one partition per group, so a group's balance
                // mutations stay in publication order regardless of which kind they are.
                String.valueOf(groupId),
                event);

        return saved;
    }

    private void validate(Long groupId, RecordPaymentRequest req) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (req.getFromUserId().equals(req.getToUserId())) {
            throw new IllegalArgumentException(
                    "fromUserId and toUserId must differ; user " + req.getFromUserId()
                            + " cannot settle with themselves");
        }
        ShareAllocator.requireRepresentable(req.getAmount());
    }

    @Transactional(readOnly = true)
    public List<Payment> getPayments(Long groupId) {
        return paymentRepo.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}
