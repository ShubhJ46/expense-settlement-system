package com.project.Splitwise.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class BalanceUpdatedEvent {
    private final Long groupId;
    private final List<UserBalance> userBalances;

    public record UserBalance(Long userId, BigDecimal net) {}
}
