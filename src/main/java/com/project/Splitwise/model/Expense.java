package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A bill somebody fronted, to be divided among the group.
 *
 * <p>Immutable once written: there is no edit or delete. That is a deliberate limitation
 * rather than an oversight — an expense has already been projected into balances by the time
 * anyone could want to change it, so a correction has to be a compensating entry rather than
 * a mutation, and none of that flow exists yet.
 *
 * <p>The per-user breakdown lives in {@link ExpenseShare}, keyed by this row's id.
 */
@Entity
@Table(name = "expenses")
@Data
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long groupId;

    /** Who actually paid the bill; they are credited the full amount before shares apply. */
    private Long paidBy;

    /** The total. Must equal the sum of this expense's shares. */
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    private String description;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
