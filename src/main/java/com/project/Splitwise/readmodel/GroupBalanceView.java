package com.project.Splitwise.readmodel;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Read-side projection of {@link com.project.Splitwise.model.Balance}, maintained by
 * {@link com.project.Splitwise.readmodel.consumer.BalanceViewConsumer} from the
 * {@code balance-updated} topic.
 *
 * <p>It carries the same numbers and the same sign convention as the write model — positive
 * is owed, negative owes — so why does it exist at all? Because queries and mutations have
 * different needs. Balance queries are the most frequent operation in the product and want
 * a table nothing contends on, while the write model is being updated by consumers and must
 * stay locked as briefly as possible. Separating them lets each scale independently, which
 * is the entire argument for CQRS here.
 *
 * <p>Consequence: this table is <em>eventually</em> consistent. A read taken immediately
 * after a write may not include it yet. For a balance display that is fine; for anything
 * that must not be stale, read the write model through
 * {@link com.project.Splitwise.service.SettlementService} instead.
 */
@Entity
@Table(name = "group_balance_view")
@IdClass(GroupBalanceViewId.class)
public class GroupBalanceView {

    @Id
    private Long groupId;

    @Id
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal netBalance;

    protected GroupBalanceView() {}

    public GroupBalanceView(Long groupId, Long userId, BigDecimal netBalance) {
        this.groupId = groupId;
        this.userId = userId;
        this.netBalance = netBalance;
    }

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public BigDecimal getNetBalance() { return netBalance; }
}
