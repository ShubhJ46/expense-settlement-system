package com.project.Splitwise.domain.event;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@Data
public class ExpenseCreatedEvent {

    private final Long expenseId;
    private final Long groupId;
    private final Long paidBy;
    private final BigDecimal amount;
    private final List<Share> shares;

    public record Share(Long userId, BigDecimal amount) {}
}
