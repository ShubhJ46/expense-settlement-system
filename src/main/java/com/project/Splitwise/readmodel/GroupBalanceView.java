package com.project.Splitwise.readmodel;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "group_balance_view")
@IdClass(GroupBalanceViewId.class)
public class GroupBalanceView {

    @Id
    private Long groupId;

    @Id
    private Long userId;

    @Column(nullable = false)
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
