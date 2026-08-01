package com.project.Splitwise.factory;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.model.Expense;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ExpenseEventFactory {

    /**
     * The generated {@code eventId} is the deduplication key consumers use. It is minted
     * once here and then persisted in the outbox, so a relay retry republishes the same id
     * rather than a fresh one — which is what makes consumer-side dedup actually work.
     */
    public ExpenseCreatedEvent createExpenseCreatedEvent(
            Expense expense,
            List<CreateExpenseRequest.Share> shares) {

        return ExpenseCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .expenseId(expense.getId())
                .groupId(expense.getGroupId())
                .paidBy(expense.getPaidBy())
                .amount(expense.getAmount())
                .shares(shares.stream()
                        .map(s -> new ExpenseCreatedEvent.Share(s.getUserId(), s.getAmount()))
                        .toList())
                .build();
    }
}
