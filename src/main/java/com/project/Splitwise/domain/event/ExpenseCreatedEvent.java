package com.project.Splitwise.domain.event;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseCreatedEvent {

    private String eventId;
    private  Long expenseId;
    private  Long groupId;
    private  Long paidBy;
    private  BigDecimal amount;
    private  List<Share> shares;

    public record Share(Long userId, BigDecimal amount) {}
}
