package com.project.Splitwise.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecordPaymentRequest {

    @NotNull
    private Long fromUserId;

    @NotNull
    private Long toUserId;

    /**
     * Must be positive. Direction is carried by the two user ids, not by the sign, so a
     * negative amount would silently invert the transfer.
     */
    @NotNull
    @Positive
    private BigDecimal amount;

    private String note;
}
