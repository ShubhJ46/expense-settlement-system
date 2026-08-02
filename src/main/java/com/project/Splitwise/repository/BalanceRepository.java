package com.project.Splitwise.repository;

import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.BalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * The authoritative balance rows. Everything here is scoped by group — there is deliberately
 * no "all balances" query, because no legitimate operation in the service wants one.
 */
public interface BalanceRepository extends JpaRepository<Balance, BalanceId> {

    /** The read half of the read-modify-write in {@code BalanceService.applyDelta}. */
    Optional<Balance> findByGroupIdAndUserId(Long groupId, Long userId);

    /** Whole-group read, used for settlement computation and for the outgoing snapshot. */
    List<Balance> findByGroupId(Long groupId);
}
