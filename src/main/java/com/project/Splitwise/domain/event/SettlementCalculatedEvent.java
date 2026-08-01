package com.project.Splitwise.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The full settlement plan for a group, recomputed whenever its balances change.
 *
 * <p>Carries the entire transfer list rather than one transfer per event on purpose: the
 * projection it feeds is a wholesale replacement of the group's rows, and splitting the
 * plan across several events would leave the read model showing a mix of two different
 * plans in between.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SettlementCalculatedEvent {

    private Long groupId;
    private List<Transfer> transfers;

    public record Transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
    }
}
