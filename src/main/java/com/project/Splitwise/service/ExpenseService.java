package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.factory.ExpenseEventFactory;
import com.project.Splitwise.kafka.ExpenseEventProducer;
import com.project.Splitwise.model.Expense;
import com.project.Splitwise.model.ExpenseShare;
import com.project.Splitwise.repository.ExpenseRepository;
import com.project.Splitwise.repository.ExpenseShareRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final ExpenseShareRepository shareRepo;
    private final ExpenseEventFactory eventFactory;
    private final ExpenseEventProducer producer;

    public Expense createExpense(CreateExpenseRequest req) {
        validateShares(req);

        Expense expense = new Expense();
        expense.setGroupId(req.getGroupId());
        expense.setPaidBy(req.getPaidBy());
        expense.setAmount(req.getAmount());

        Expense saved = expenseRepo.save(expense);

        saveShares(saved.getId(), req.getShares());

        ExpenseCreatedEvent event =
                eventFactory.createdExpenseCreatedEvent(saved, req.getShares());

        producer.publish(event);

        return saved;
    }

    private void validateShares(CreateExpenseRequest req) {
        BigDecimal shareSum = req.getShares().stream()
                .map(share -> share.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (shareSum.compareTo(req.getAmount()) != 0) {
            throw new IllegalArgumentException("Share total must equal expense amount");
        }
    }

    private void saveShares(Long expenseId, List<CreateExpenseRequest.Share> shares) {
        for (var share: shares) {
            ExpenseShare es = new ExpenseShare();
            es.setExpenseId(expenseId);
            es.setUserId(share.userId);
            es.setShareAmount(share.amount);
            shareRepo.save(es);
        }
    }

    public List<Expense> getAllExpenses() {
        return expenseRepo.findAll();
    }
}
