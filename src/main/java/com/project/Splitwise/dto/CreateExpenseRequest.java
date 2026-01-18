package com.project.Splitwise.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateExpenseRequest {

    @NotNull
    private Long groupId;

    @NotNull
    private Long paidBy;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotEmpty
    private List<Share> shares;

    @Data
    public static class Share {
        public Long userId;
        public BigDecimal amount;
    }
}
