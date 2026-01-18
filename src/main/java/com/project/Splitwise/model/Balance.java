package com.project.Splitwise.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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

    private BigDecimal netBalance;

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
