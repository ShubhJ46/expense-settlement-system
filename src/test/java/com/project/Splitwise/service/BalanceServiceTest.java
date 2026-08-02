package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.domain.event.GroupBalancesChangedEvent;
import com.project.Splitwise.domain.event.PaymentRecordedEvent;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.repository.BalanceRepository;
import com.project.Splitwise.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BalanceServiceTest {

    private static final Long GROUP_ID = 7L;

    @Mock
    private BalanceRepository balanceRepo;
    @Mock
    private ProcessedEventRepository processedRepo;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BalanceService balanceService;

    /** Stands in for the balances table so deltas actually accumulate across calls. */
    private Map<Long, Balance> stored;

    /** Real meters over an in-memory registry, so counter assertions test the actual wiring. */
    private SplitwiseMetrics metrics;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        stored = new HashMap<>();
        meterRegistry = new SimpleMeterRegistry();
        metrics = new SplitwiseMetrics(meterRegistry);
        balanceService = new BalanceService(balanceRepo, processedRepo, eventPublisher, metrics);

        when(balanceRepo.findByGroupIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<Long>getArgument(1))));
        when(balanceRepo.save(any(Balance.class))).thenAnswer(inv -> {
            Balance b = inv.getArgument(0);
            stored.put(b.getUserId(), b);
            return b;
        });
    }

    /** Fixed so the propagation of occurredAt into the published signal is assertable. */
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static ExpenseCreatedEvent expense(String eventId, long paidBy, String amount,
                                               Map<Long, String> shares) {
        return ExpenseCreatedEvent.builder()
                .eventId(eventId)
                .occurredAt(OCCURRED_AT)
                .expenseId(1L)
                .groupId(GROUP_ID)
                .paidBy(paidBy)
                .amount(new BigDecimal(amount))
                .shares(shares.entrySet().stream()
                        .map(e -> new ExpenseCreatedEvent.Share(e.getKey(), new BigDecimal(e.getValue())))
                        .toList())
                .build();
    }

    @Test
    @DisplayName("payer nets out to what they are owed, not the full bill")
    void payerIsCreditedNetOfTheirOwnShare() {
        when(processedRepo.existsById("evt-1")).thenReturn(false);

        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));

        assertEquals(0, stored.get(1L).getNetBalance().compareTo(new BigDecimal("200.00")));
        assertEquals(0, stored.get(2L).getNetBalance().compareTo(new BigDecimal("-100.00")));
        assertEquals(0, stored.get(3L).getNetBalance().compareTo(new BigDecimal("-100.00")));
    }

    @Test
    @DisplayName("group balances always sum to zero")
    void balancesSumToZero() {
        when(processedRepo.existsById(any())).thenReturn(false);

        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));
        balanceService.handleExpense(expense("evt-2", 2L, "60.00",
                Map.of(1L, "20.00", 2L, "20.00", 3L, "20.00")));

        BigDecimal total = stored.values().stream()
                .map(Balance::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, total.compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("redelivery of the same event does not double-count: this is the effectively-once guarantee")
    void redeliveryIsANoOp() {
        ExpenseCreatedEvent event = expense("evt-dup", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00"));

        when(processedRepo.existsById("evt-dup")).thenReturn(false);
        balanceService.handleExpense(event);

        BigDecimal afterFirst = stored.get(1L).getNetBalance();

        // Second delivery of the identical record, exactly as Kafka would replay it.
        when(processedRepo.existsById("evt-dup")).thenReturn(true);
        balanceService.handleExpense(event);

        assertEquals(0, stored.get(1L).getNetBalance().compareTo(afterFirst));
        verify(processedRepo, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    void publishesExactlyOneSignalPerExpense() {
        when(processedRepo.existsById("evt-1")).thenReturn(false);

        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());

        // Three participants used to mean three full-group snapshots on the wire. The
        // originating timestamp rides along so the projection can report end-to-end latency.
        assertEquals(List.of(new GroupBalancesChangedEvent(GROUP_ID, OCCURRED_AT)),
                captor.getAllValues());
    }

    @Test
    @DisplayName("applied and duplicate events are counted separately")
    void processingOutcomesAreCounted() {
        ExpenseCreatedEvent event = expense("evt-counted", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00"));

        when(processedRepo.existsById("evt-counted")).thenReturn(false);
        balanceService.handleExpense(event);

        when(processedRepo.existsById("evt-counted")).thenReturn(true);
        balanceService.handleExpense(event);
        balanceService.handleExpense(event);

        assertEquals(1.0, meterRegistry.get("splitwise.events.processed")
                .tag("outcome", "applied").counter().count());
        // The ratio of these two is the redelivery rate, which is why they are one meter
        // separated by a tag rather than two unrelated counters.
        assertEquals(2.0, meterRegistry.get("splitwise.events.processed")
                .tag("outcome", "duplicate").counter().count());
    }

    @Test
    void alreadyProcessedEventPublishesNothing() {
        when(processedRepo.existsById("evt-old")).thenReturn(true);

        balanceService.handleExpense(expense("evt-old", 1L, "300.00", Map.of(1L, "300.00")));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(balanceRepo, never()).save(any(Balance.class));
    }

    private static PaymentRecordedEvent payment(String eventId, long from, long to, String amount) {
        return PaymentRecordedEvent.builder()
                .eventId(eventId)
                .paymentId(1L)
                .groupId(GROUP_ID)
                .fromUserId(from)
                .toUserId(to)
                .amount(new BigDecimal(amount))
                .build();
    }

    @Test
    @DisplayName("paying a debt in full returns both parties to zero")
    void paymentDischargesTheDebt() {
        when(processedRepo.existsById(any())).thenReturn(false);

        // User 2 and 3 each owe user 1 a hundred.
        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));

        // User 2 settles up.
        balanceService.handlePayment(payment("pay-1", 2L, 1L, "100.00"));

        assertEquals(0, stored.get(2L).getNetBalance().compareTo(BigDecimal.ZERO),
                "the payer's debt is discharged");
        assertEquals(0, stored.get(1L).getNetBalance().compareTo(new BigDecimal("100.00")),
                "the payee is still owed the other share");
        assertEquals(0, stored.get(3L).getNetBalance().compareTo(new BigDecimal("-100.00")));
    }

    @Test
    @DisplayName("payments keep the group summing to zero")
    void paymentPreservesZeroSum() {
        when(processedRepo.existsById(any())).thenReturn(false);

        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));
        balanceService.handlePayment(payment("pay-1", 2L, 1L, "40.00"));

        BigDecimal total = stored.values().stream()
                .map(Balance::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, total.compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("overpaying flips the position rather than clamping at zero")
    void overpaymentInvertsTheDebt() {
        when(processedRepo.existsById(any())).thenReturn(false);

        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));
        balanceService.handlePayment(payment("pay-1", 2L, 1L, "150.00"));

        assertEquals(0, stored.get(2L).getNetBalance().compareTo(new BigDecimal("50.00")),
                "having overpaid by 50, user 2 is now owed 50");
    }

    @Test
    @DisplayName("a redelivered payment does not discharge the debt twice")
    void redeliveredPaymentIsANoOp() {
        when(processedRepo.existsById(any())).thenReturn(false);
        balanceService.handleExpense(expense("evt-1", 1L, "300.00",
                Map.of(1L, "100.00", 2L, "100.00", 3L, "100.00")));

        PaymentRecordedEvent event = payment("pay-dup", 2L, 1L, "100.00");
        balanceService.handlePayment(event);
        BigDecimal afterFirst = stored.get(2L).getNetBalance();

        when(processedRepo.existsById("pay-dup")).thenReturn(true);
        balanceService.handlePayment(event);

        assertEquals(0, stored.get(2L).getNetBalance().compareTo(afterFirst));
    }
}
