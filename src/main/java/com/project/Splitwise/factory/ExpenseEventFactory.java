package com.project.Splitwise.factory;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.model.Expense;
import org.springframework.stereotype.Component;

import java.util.List;

@Component

public class ExpenseEventFactory {

    public ExpenseCreatedEvent createdExpenseCreatedEvent(
            Expense expense,
            List<CreateExpenseRequest.Share> shares
    ) {
        return ExpenseCreatedEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .expenseId(expense.getId())
                .groupId(expense.getGroupId())
                .paidBy(expense.getPaidBy())
                .amount(expense.getAmount())
                .shares(
                shares.stream()
                    .map(s -> new ExpenseCreatedEvent.Share(
                            s.getUserId(),
                            s.getAmount()
                    ))
                    .toList()
                )
                .build();
    }
}
