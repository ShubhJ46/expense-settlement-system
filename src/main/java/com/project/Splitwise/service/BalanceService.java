package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.domain.event.GroupBalancesChangedEvent;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.repository.BalanceRepository;
import com.project.Splitwise.repository.ProcessedEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final BalanceRepository balanceRepo;
    private final ProcessedEventRepository processedRepo;
    private final ApplicationEventPublisher eventPublisher;

    public BalanceService(BalanceRepository balanceRepo,
                          ProcessedEventRepository processedRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.balanceRepo = balanceRepo;
        this.processedRepo = processedRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Applies one expense to the group's net balances.
     *
     * <p>Delivery from Kafka is at-least-once, so this method is the deduplication point:
     * the {@code processed_events} row and the balance mutations commit in the same
     * transaction, which makes redelivery a no-op. That combination is
     * <em>effectively-once</em> processing — not Kafka exactly-once semantics, which would
     * additionally require a transactional producer and {@code read_committed} consumers.
     */
    @Transactional
    public void handleExpense(ExpenseCreatedEvent event) {
        if (processedRepo.existsById(event.getEventId())) {
            return;
        }

        Long groupId = event.getGroupId();

        // The payer fronted the whole bill, so they are owed all of it...
        applyDelta(groupId, event.getPaidBy(), event.getAmount());

        // ...and then everyone, payer included, absorbs their own share. The payer's net
        // therefore lands on (amount - their share), which is what they are actually owed.
        for (var share : event.getShares()) {
            applyDelta(groupId, share.userId(), share.amount().negate());
        }

        processedRepo.save(new ProcessedEvent(event.getEventId()));

        // One signal per expense, not per participant. Published to Kafka only after this
        // transaction commits (see BalanceKafkaPublisher).
        eventPublisher.publishEvent(new GroupBalancesChangedEvent(groupId));
    }

    private void applyDelta(Long groupId, Long userId, BigDecimal delta) {
        Balance balance = balanceRepo
                .findByGroupIdAndUserId(groupId, userId)
                .orElseGet(() -> new Balance(groupId, userId, BigDecimal.ZERO));

        balance.add(delta);
        balanceRepo.save(balance);
    }
}
