package com.project.Splitwise.dto;

import com.project.Splitwise.domain.settlement.Settlement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Response DTO; the no-arg constructor is what lets a client deserialize it back. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SettlementResponse {
    private Long groupId;
    private List<Settlement> settlements;
}
