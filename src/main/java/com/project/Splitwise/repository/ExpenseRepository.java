package com.project.Splitwise.repository;

import com.project.Splitwise.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
List<Expense> findAll();
}
