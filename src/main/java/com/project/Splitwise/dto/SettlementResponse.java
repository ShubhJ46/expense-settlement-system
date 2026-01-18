package com.project.Splitwise.dto;

import com.project.Splitwise.domain.settlement.Settlement;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SettlementResponse {
    private Long groupId;
    private List<Settlement> settlements;
}
