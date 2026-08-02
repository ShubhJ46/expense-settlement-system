package com.project.Splitwise.controller;

import com.project.Splitwise.dto.CreateExpenseRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import com.project.Splitwise.model.Expense;
import com.project.Splitwise.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /** Caps how much one request can pull, regardless of what the caller asks for. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Scoped to a single group, which the caller must belong to, and paged. */
    @GetMapping
    public Page<Expense> getExpenses(@RequestParam("groupId") Long groupId,
                                     @RequestParam(value = "page", defaultValue = "0") int page,
                                     @RequestParam(value = "size", defaultValue = "20") int size) {
        int capped = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.getExpenses(groupId,
                PageRequest.of(Math.max(page, 0), capped, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * Creates an expense.
     *
     * <p>{@code Idempotency-Key} is optional and follows the convention every payments API
     * uses: send the same key on a retry and the original expense comes back rather than a
     * second one. Reusing a key with a different body is a client bug and is refused with a
     * 409 rather than answered with the earlier response.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody @Valid CreateExpenseRequest req,
                                    @RequestHeader(value = "Idempotency-Key", required = false)
                                    String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createExpense(req, idempotencyKey));
    }
}
