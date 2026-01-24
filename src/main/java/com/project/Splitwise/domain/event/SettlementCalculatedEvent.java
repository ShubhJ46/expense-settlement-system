package com.project.Splitwise.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCalculatedEvent {
    private Long groupId;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
}
