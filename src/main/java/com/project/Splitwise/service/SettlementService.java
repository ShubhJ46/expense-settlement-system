package com.project.Splitwise.service;

import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.domain.settlement.UserBalance;
import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.repository.BalanceRepository;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SettlementService {

    private final BalanceRepository balanceRepository;
    private final GroupAccess groupAccess;

    public SettlementService(BalanceRepository balanceRepository, GroupAccess groupAccess) {
        this.balanceRepository = balanceRepository;
        this.groupAccess = groupAccess;
    }

    /**
     * Computes settlements on demand from the authoritative write model.
     *
     * <p>Entities are copied into {@link UserBalance} before reaching the engine so the
     * settlement arithmetic cannot dirty the persistence context. {@code readOnly} makes
     * that guarantee explicit at the transaction boundary as well.
     */
    @Transactional(readOnly = true)
    public SettlementResponse getSettlements(Long groupId) {
        groupAccess.requireMember(groupId);

        List<UserBalance> positions = balanceRepository.findByGroupId(groupId).stream()
                .map(b -> new UserBalance(b.getUserId(), b.getNetBalance()))
                .toList();

        List<Settlement> settlements = SettlementEngine.settle(positions);

        return new SettlementResponse(groupId, settlements);
    }
}
