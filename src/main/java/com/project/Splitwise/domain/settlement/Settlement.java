package com.project.Splitwise.domain.settlement;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class Settlement {
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
}
