package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A settlement that actually happened: {@code fromUserId} handed {@code amount} to
 * {@code toUserId}.
 *
 * <p>Deliberately distinct from {@link com.project.Splitwise.domain.settlement.Settlement},
 * which is a <em>suggestion</em> produced by the settlement engine and never persisted. The
 * engine proposes a plan; this records that a human went and paid one of its legs, or any
 * other amount they felt like. Nothing requires a payment to match a suggested leg.
 */
@Entity
@Table(name = "payments")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;
    private Long fromUserId;
    private Long toUserId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    private String note;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
