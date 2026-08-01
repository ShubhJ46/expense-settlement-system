package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.domain.event.GroupBalancesChangedEvent;
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

import java.math.BigDecimal;
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

    @BeforeEach
    void setUp() {
        stored = new HashMap<>();
        balanceService = new BalanceService(balanceRepo, processedRepo, eventPublisher);

        when(balanceRepo.findByGroupIdAndUserId(anyLong(), anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(stored.get(inv.<Long>getArgument(1))));
        when(balanceRepo.save(any(Balance.class))).thenAnswer(inv -> {
            Balance b = inv.getArgument(0);
            stored.put(b.getUserId(), b);
            return b;
        });
    }

    private static ExpenseCreatedEvent expense(String eventId, long paidBy, String amount,
                                               Map<Long, String> shares) {
        return ExpenseCreatedEvent.builder()
                .eventId(eventId)
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

        // Three participants used to mean three full-group snapshots on the wire.
        assertEquals(List.of(new GroupBalancesChangedEvent(GROUP_ID)), captor.getAllValues());
    }

    @Test
    void alreadyProcessedEventPublishesNothing() {
        when(processedRepo.existsById("evt-old")).thenReturn(true);

        balanceService.handleExpense(expense("evt-old", 1L, "300.00", Map.of(1L, "300.00")));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
        verify(balanceRepo, never()).save(any(Balance.class));
    }
}
