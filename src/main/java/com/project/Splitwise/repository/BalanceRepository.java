package com.project.Splitwise.repository;

import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.BalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, BalanceId> {
    Optional<Balance> findByGroupIdAndUserId(Long groupId, Long userId);
    List<Balance> findByGroupId(Long groupId);
}
