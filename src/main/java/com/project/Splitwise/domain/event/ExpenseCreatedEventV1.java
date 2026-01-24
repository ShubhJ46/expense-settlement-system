package com.project.Splitwise.domain.event;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ExpenseCreatedEventV1 {

    private Long eventId;
    private Long expenseId;
    private Long groupId;
    private Long paidBy;
    private BigDecimal amount;
    private List<Share> shares;

    public record Share(Long userId, BigDecimal amount) {}
}
