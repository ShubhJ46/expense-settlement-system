package com.project.Splitwise.controller;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/expenses")
@Slf4j
public class ExpenseController {
    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);
    private final ExpenseService service;

    public ExpenseController(ExpenseService expenseService) {
        this.service = expenseService;
    }

    @GetMapping
    public ResponseEntity<?> getAllExpenses() {
        log.info("Fetching all expenses");
        return ResponseEntity.ok(service.getAllExpenses());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateExpenseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createExpense(req));
    }
}
