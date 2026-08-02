package com.project.Splitwise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One user's net position in one group — the authoritative write model.
 *
 * <p><strong>Sign convention:</strong> positive means the user is <em>owed</em> money,
 * negative means they <em>owe</em> it. Every other class in the service depends on this
 * reading, so it is worth stating once here: when user 1 pays a 300 bill split three ways,
 * they land on +200 (they fronted 300 and consumed 100 of it), and the other two land on
 * -100 each.
 *
 * <p><strong>Invariant:</strong> the balances of a group always sum to zero. Money is only
 * ever moved between members, never created, so any non-zero total is a bug — which is why
 * several tests assert exactly that.
 *
 * <p>Storing a net figure per user rather than a graph of individual debts is the design
 * decision that makes the rest simple. Cycles (A owes B owes C owes A) cancel out on their
 * own instead of needing to be detected and unwound, and a settlement plan is derived by
 * matching negatives against positives rather than by traversing edges.
 */
@Entity
@Table(name = "balances")
@IdClass(BalanceId.class)
@Data
public class Balance {

    @Id
    private Long groupId;

    @Id
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBalance;

    /**
     * Guards the read-modify-write in {@code BalanceService.applyDelta}.
     *
     * <p>Expenses alone could not race: they are keyed by group, so one partition and one
     * consumer thread applied them in order. Settlement payments arrive on a second topic
     * with its own consumer, so two threads can now reach the same row concurrently and the
     * later write would otherwise clobber the earlier one. The version turns that into an
     * optimistic-lock failure, which the Kafka error handler retries.
     */
    @Version
    private Long version;

    protected Balance(){}

    public Balance(Long groupId, Long userId, BigDecimal netBalance) {
        this.groupId = groupId;
        this.userId = userId;
        this.netBalance = netBalance;
    }

    public void add(BigDecimal delta) {
        this.netBalance = this.netBalance.add(delta);
    }

}
