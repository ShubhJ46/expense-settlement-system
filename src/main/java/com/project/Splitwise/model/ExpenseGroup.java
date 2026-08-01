package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Named {@code ExpenseGroup} rather than {@code Group} because {@code group} is a reserved
 * word in SQL; the table is {@code expense_groups} for the same reason.
 */
@Entity
@Table(name = "expense_groups")
@Data
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
