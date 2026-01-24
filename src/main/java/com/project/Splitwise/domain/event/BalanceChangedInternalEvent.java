package com.project.Splitwise.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceChangedInternalEvent {
    Long groupId;
    Long userId;
    BigDecimal netBalance;
}
