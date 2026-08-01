package com.project.Splitwise.domain.settlement;

import java.math.BigDecimal;

/**
 * Immutable snapshot of one user's net position inside a group.
 *
 * <p>Deliberately kept separate from the {@code Balance} JPA entity: the settlement
 * algorithm subtracts from balances as it matches debtors to creditors, and doing
 * that to a managed entity would let Hibernate flush the half-settled values back
 * to the {@code balances} table on what is supposed to be a read-only path.
 */
public record UserBalance(Long userId, BigDecimal netBalance) {
}
