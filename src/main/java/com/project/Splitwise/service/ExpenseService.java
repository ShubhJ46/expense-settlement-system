package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.factory.ExpenseEventFactory;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.Expense;
import com.project.Splitwise.model.ExpenseShare;
import com.project.Splitwise.outbox.OutboxWriter;
import com.project.Splitwise.repository.ExpenseRepository;
import com.project.Splitwise.repository.ExpenseShareRepository;
import com.project.Splitwise.security.GroupAccess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExpenseService {

    private static final String EXPENSE_TOPIC = "expense-created";

    private final ExpenseRepository expenseRepo;
    private final ExpenseShareRepository shareRepo;
    private final ExpenseEventFactory eventFactory;
    private final OutboxWriter outboxWriter;
    private final GroupAccess groupAccess;
    private final SplitwiseMetrics metrics;

    public ExpenseService(ExpenseRepository expenseRepo,
                          ExpenseShareRepository shareRepo,
                          ExpenseEventFactory eventFactory,
                          OutboxWriter outboxWriter,
                          GroupAccess groupAccess,
                          SplitwiseMetrics metrics) {
        this.expenseRepo = expenseRepo;
        this.shareRepo = shareRepo;
        this.eventFactory = eventFactory;
        this.outboxWriter = outboxWriter;
        this.groupAccess = groupAccess;
        this.metrics = metrics;
    }

    /**
     * Persists an expense and stages its event atomically.
     *
     * <p>Nothing here touches Kafka. The expense rows, the share rows and the outbox row
     * commit together or not at all, and {@link com.project.Splitwise.outbox.OutboxRelay}
     * publishes afterwards. That is what removes the old failure mode where a broker
     * timeout after the DB commit lost the balance update permanently.
     */
    @Transactional
    public Expense createExpense(CreateExpenseRequest req) {
        // Authorization before anything else: the caller must be in the group, and so must
        // everyone the expense touches. Without the second check a legitimate member could
        // charge a share to somebody who was never in the group.
        groupAccess.requireMember(req.getGroupId());

        List<CreateExpenseRequest.Share> shares = resolveShares(req);

        List<Long> participants = new ArrayList<>();
        participants.add(req.getPaidBy());
        shares.forEach(s -> participants.add(s.getUserId()));
        groupAccess.requireAllMembers(req.getGroupId(), participants);

        Expense expense = new Expense();
        expense.setGroupId(req.getGroupId());
        expense.setPaidBy(req.getPaidBy());
        expense.setAmount(req.getAmount());
        expense.setDescription(req.getDescription());

        Expense saved = expenseRepo.save(expense);
        saveShares(saved.getId(), shares);

        ExpenseCreatedEvent event = eventFactory.createExpenseCreatedEvent(saved, shares);

        outboxWriter.append(
                "Expense",
                String.valueOf(saved.getId()),
                EXPENSE_TOPIC,
                // Keyed by group so every expense for a group lands on one partition and
                // balance mutations for that group are applied in publication order.
                String.valueOf(saved.getGroupId()),
                event);

        metrics.expenseCreated();
        return saved;
    }

    /**
     * Turns whatever split the caller asked for into explicit per-user shares.
     *
     * <p>EQUAL is computed server-side because that is where the rounding problem actually
     * lives: a client dividing 10.00 by 3 and sending 3.33 three times is 1 paisa short,
     * and that gap accumulates in the balances forever.
     */
    private List<CreateExpenseRequest.Share> resolveShares(CreateExpenseRequest req) {
        return switch (req.getSplitType()) {
            case EQUAL -> equalShares(req);
            case EXACT -> validatedExactShares(req);
        };
    }

    private List<CreateExpenseRequest.Share> equalShares(CreateExpenseRequest req) {
        List<Long> participants = req.getParticipants();
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("participants is required when splitType=EQUAL");
        }
        if (participants.stream().distinct().count() != participants.size()) {
            throw new IllegalArgumentException("participants contains duplicates");
        }

        List<BigDecimal> amounts = ShareAllocator.allocateEqually(req.getAmount(), participants.size());

        List<CreateExpenseRequest.Share> shares = new ArrayList<>(participants.size());
        for (int i = 0; i < participants.size(); i++) {
            CreateExpenseRequest.Share share = new CreateExpenseRequest.Share();
            share.setUserId(participants.get(i));
            share.setAmount(amounts.get(i));
            shares.add(share);
        }
        return shares;
    }

    private List<CreateExpenseRequest.Share> validatedExactShares(CreateExpenseRequest req) {
        List<CreateExpenseRequest.Share> shares = req.getShares();
        if (shares == null || shares.isEmpty()) {
            throw new IllegalArgumentException("shares is required when splitType=EXACT");
        }
        if (shares.stream().anyMatch(s -> s.getUserId() == null || s.getAmount() == null)) {
            throw new IllegalArgumentException("every share needs a userId and an amount");
        }

        BigDecimal shareSum = shares.stream()
                .map(CreateExpenseRequest.Share::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (shareSum.compareTo(req.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Shares sum to " + shareSum + " but expense amount is " + req.getAmount());
        }
        return shares;
    }

    private void saveShares(Long expenseId, List<CreateExpenseRequest.Share> shares) {
        for (CreateExpenseRequest.Share share : shares) {
            ExpenseShare es = new ExpenseShare();
            es.setExpenseId(expenseId);
            es.setUserId(share.getUserId());
            es.setShareAmount(share.getAmount());
            shareRepo.save(es);
        }
    }

    /**
     * Expenses for one group, a page at a time.
     *
     * <p>This replaces an unscoped, unpaginated {@code findAll()}. That was a mild
     * performance wart before there was any concept of a caller; with authorization in place
     * it would be an outright leak, handing every expense in the system to anyone who asked.
     */
    @Transactional(readOnly = true)
    public Page<Expense> getExpenses(Long groupId, Pageable pageable) {
        groupAccess.requireMember(groupId);
        return expenseRepo.findByGroupId(groupId, pageable);
    }
}
