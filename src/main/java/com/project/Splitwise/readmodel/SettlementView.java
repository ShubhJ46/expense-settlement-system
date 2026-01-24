package com.project.Splitwise.readmodel;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "settlement_view")
public class SettlementView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long fromUser;
    private Long toUser;

    @Column(nullable = false)
    private BigDecimal amount;

    protected SettlementView() {}

    public SettlementView(Long groupId, Long fromUser, Long toUser, BigDecimal amount) {
        this.groupId = groupId;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
    }

    public Long getGroupId() { return groupId; }
    public Long getFromUser() { return fromUser; }
    public Long getToUser() { return toUser; }
    public BigDecimal getAmount() { return amount; }

}
