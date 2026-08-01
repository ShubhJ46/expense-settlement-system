package com.project.Splitwise.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateExpenseRequest {

    public enum SplitType {
        /** Server divides the amount evenly across {@code participants}, penny-exact. */
        EQUAL,
        /** Caller supplies each user's exact share; they must sum to {@code amount}. */
        EXACT
    }

    @NotNull
    private Long groupId;

    @NotNull
    private Long paidBy;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String description;

    /** Defaults to EXACT so existing callers keep their current behaviour. */
    @NotNull
    private SplitType splitType = SplitType.EXACT;

    /** Required for {@link SplitType#EQUAL}. */
    private List<Long> participants;

    /** Required for {@link SplitType#EXACT}. */
    private List<Share> shares;

    @Data
    public static class Share {
        private Long userId;
        private BigDecimal amount;
    }
}
