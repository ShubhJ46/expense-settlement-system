package com.project.Splitwise.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdatedEvent {

    private Long groupId;
    private List<UserBalance> balances;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserBalance {
        private Long userId;
        private BigDecimal netBalance;
    }
}
