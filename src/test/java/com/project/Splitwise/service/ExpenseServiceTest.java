package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.factory.ExpenseEventFactory;
import com.project.Splitwise.model.Expense;
import com.project.Splitwise.model.ExpenseShare;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import java.util.Optional;
import com.project.Splitwise.outbox.OutboxWriter;
import com.project.Splitwise.security.GroupAccess;
import com.project.Splitwise.repository.ExpenseRepository;
import com.project.Splitwise.repository.ExpenseShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepo;
    @Mock
    private ExpenseShareRepository shareRepo;
    @Mock
    private OutboxWriter outboxWriter;
    /** Permissive by default; the authorization rules themselves are covered in GroupAccessTest. */
    @Mock
    private GroupAccess groupAccess;

    /** Permissive by default; idempotency behaviour is covered in IdempotencyIT and its own test. */
    @Mock
    private IdempotencyGuard idempotency;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(
                expenseRepo, shareRepo, new ExpenseEventFactory(), outboxWriter, groupAccess,
                new SplitwiseMetrics(new SimpleMeterRegistry()), idempotency);

        when(expenseRepo.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });
        when(shareRepo.save(any(ExpenseShare.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CreateExpenseRequest equalSplit(String amount, Long... participants) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(1L);
        req.setPaidBy(participants[0]);
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(List.of(participants));
        return req;
    }

    private ExpenseCreatedEvent capturePublishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxWriter).append(any(), any(), eq("expense-created"), any(), captor.capture());
        return (ExpenseCreatedEvent) captor.getValue();
    }

    @Test
    @DisplayName("an equal split of an indivisible amount still sums to the exact total")
    void equalSplitIsPennyExact() {
        expenseService.createExpense(equalSplit("10.00", 1L, 2L, 3L));

        ExpenseCreatedEvent event = capturePublishedEvent();

        BigDecimal shareTotal = event.getShares().stream()
                .map(ExpenseCreatedEvent.Share::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(3, event.getShares().size());
        assertEquals(0, shareTotal.compareTo(new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("the event goes to the outbox, never straight to Kafka")
    void stagesEventInOutboxKeyedByGroup() {
        expenseService.createExpense(equalSplit("30.00", 1L, 2L, 3L));

        verify(outboxWriter).append(
                eq("Expense"),
                eq("99"),
                eq("expense-created"),
                eq("1"),
                any(ExpenseCreatedEvent.class));
    }

    @Test
    void rejectsExactSharesThatDoNotSumToTheTotal() {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(1L);
        req.setPaidBy(1L);
        req.setAmount(new BigDecimal("100.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EXACT);

        CreateExpenseRequest.Share share = new CreateExpenseRequest.Share();
        share.setUserId(1L);
        share.setAmount(new BigDecimal("99.99"));
        req.setShares(List.of(share));

        assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(req));
        // Nothing may be staged for an expense that was rejected.
        verify(outboxWriter, never()).append(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEqualSplitWithoutParticipants() {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(1L);
        req.setPaidBy(1L);
        req.setAmount(new BigDecimal("100.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);

        assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(req));
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThrows(IllegalArgumentException.class,
                () -> expenseService.createExpense(equalSplit("30.00", 1L, 2L, 2L)));
    }
}
