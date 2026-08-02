package com.project.Splitwise.readmodel;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One leg of a group's stored settlement plan: {@code fromUser} should pay {@code toUser}
 * this much to move the group toward zero.
 *
 * <p>A <em>suggestion</em>, not a record of anything that happened — payments that actually
 * occurred are {@link com.project.Splitwise.model.Payment}. Nothing obliges anyone to follow
 * these legs, and a payment need not correspond to one.
 *
 * <p>Rows are replaced wholesale per group rather than appended, because a plan is only
 * meaningful as a complete set. Appending was the original behaviour and it accumulated
 * every historical plan on top of the current one, so the endpoint returned a growing pile
 * of contradictory advice.
 */
@Entity
@Table(name = "settlement_view")
public class SettlementView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long fromUser;
    private Long toUser;

    @Column(nullable = false, precision = 19, scale = 2)
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
