package com.project.Splitwise.service;

import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.repository.BalanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SettlementService {
    private final BalanceRepository balanceRepository;


    public SettlementService(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    public SettlementResponse getSettlements(Long groupId) {
        List<Balance> balances = balanceRepository.findByGroupId(groupId);
        List<Settlement> settlements = SettlementEngine.settle(balances);

        return new SettlementResponse(groupId, settlements);
    }
}
