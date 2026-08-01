package com.project.Splitwise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.math.BigDecimal;

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
