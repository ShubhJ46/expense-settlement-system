package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "expense_shares")
@Data
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long expenseId;
    private Long userId;
    private BigDecimal shareAmount;

}
