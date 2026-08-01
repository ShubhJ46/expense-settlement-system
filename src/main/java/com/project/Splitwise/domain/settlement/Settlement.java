package com.project.Splitwise.domain.settlement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Settlement {
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
}
