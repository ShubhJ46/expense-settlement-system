package com.project.Splitwise.repository;

import com.project.Splitwise.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByGroupId(Long groupId, Pageable pageable);
}
