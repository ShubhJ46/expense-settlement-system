package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.Payment;
import com.project.Splitwise.outbox.OutboxWriter;
import com.project.Splitwise.repository.PaymentRepository;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    static final String PAYMENT_TOPIC = "payment-recorded";
    private static final String RESOURCE_TYPE = "Payment";

    private final PaymentRepository paymentRepo;
    private final OutboxWriter outboxWriter;
    private final GroupAccess groupAccess;
    private final SplitwiseMetrics metrics;
    private final IdempotencyGuard idempotency;

    public PaymentService(PaymentRepository paymentRepo,
                          OutboxWriter outboxWriter,
                          GroupAccess groupAccess,
                          SplitwiseMetrics metrics,
                          IdempotencyGuard idempotency) {
        this.paymentRepo = paymentRepo;
        this.outboxWriter = outboxWriter;
        this.groupAccess = groupAccess;
        this.metrics = metrics;
        this.idempotency = idempotency;
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
        return recordPayment(groupId, req, null);
    }

    /**
     * Records a payment, at most once per idempotency key.
     *
     * <p>More consequential here than on expenses: a retried payment that is applied twice
     * discharges a debt that was only paid once, and the money is simply gone from the
     * group's ledger.
     */
    @Transactional
    public Payment recordPayment(Long groupId, RecordPaymentRequest req, String idempotencyKey) {
        Long caller = validate(groupId, req);

        Optional<Long> replayed = idempotency.findReplay(idempotencyKey, caller, RESOURCE_TYPE, req);
        if (replayed.isPresent()) {
            return paymentRepo.findById(replayed.get()).orElseThrow(() ->
                    new IllegalStateException("Idempotency key points at a missing payment: "
                            + replayed.get()));
        }

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
                // Fixed at staging time alongside the id, so a relay retry reports the
                // original latency rather than restarting the clock.
                .occurredAt(Instant.now())
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

        idempotency.record(idempotencyKey, caller, RESOURCE_TYPE, saved.getId(), req);

        metrics.paymentRecorded();
        return saved;
    }

    /** Returns the authenticated caller, which the idempotency key is scoped to. */
    private Long validate(Long groupId, RecordPaymentRequest req) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId is required");
        }
        if (req.getFromUserId().equals(req.getToUserId())) {
            throw new IllegalArgumentException(
                    "fromUserId and toUserId must differ; user " + req.getFromUserId()
                            + " cannot settle with themselves");
        }
        ShareAllocator.requireRepresentable(req.getAmount());

        Long caller = groupAccess.requireMember(groupId);
        groupAccess.requireAllMembers(groupId, List.of(req.getFromUserId(), req.getToUserId()));

        // Being in the group is not enough to record somebody else's payment: a member could
        // otherwise clear their own debt by filing a transfer between two other people, or
        // invent a payment from someone who never made one. Either party to the transfer may
        // record it, since in practice both know it happened.
        if (!caller.equals(req.getFromUserId()) && !caller.equals(req.getToUserId())) {
            throw new AccessDeniedException(
                    "A payment can only be recorded by the payer or the payee");
        }
        return caller;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPayments(Long groupId) {
        groupAccess.requireMember(groupId);
        return paymentRepo.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}
