package com.project.Splitwise.service;

import com.project.Splitwise.domain.event.BalanceChangedInternalEvent;
import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.kafka.BalanceEventProducer;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.repository.BalanceRepository;
import com.project.Splitwise.repository.ProcessedEventRepository;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Transactional
public class BalanceService {

    private final BalanceRepository balanceRepo;
    private final ProcessedEventRepository processedRepo;
    private final BalanceEventProducer producer;
    private final ApplicationEventPublisher eventPublisher;

    public BalanceService(BalanceRepository balanceRepo,
                          ProcessedEventRepository processedRepo,
                          BalanceEventProducer producer, ApplicationEventPublisher eventPublisher) {
        this.balanceRepo = balanceRepo;
        this.processedRepo = processedRepo;
        this.producer = producer;
        this.eventPublisher = eventPublisher;
    }

    public void handleExpense(ExpenseCreatedEvent event) {

        if (processedRepo.existsById(event.getEventId())) {
            return; // Event already processed
        }

        Long groupId = event.getGroupId();

        // Credit paidBy user
        BigDecimal paidAmount = event.getAmount();
        BigDecimal paidByShare = event.getShares().stream()
                .filter(share -> share.userId().equals(event.getPaidBy()))
                .map(ExpenseCreatedEvent.Share::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        applyDelta(groupId, event.getPaidBy(), paidAmount);

        // Debit all users for their share
        for (var share : event.getShares()) {
            applyDelta(groupId, share.userId(), share.amount().negate());
        }

        processedRepo.save(new ProcessedEvent(event.getEventId()));
    }

    private void applyDelta(Long groupId, Long userId, BigDecimal delta) {
        Balance balance = balanceRepo
                .findByGroupIdAndUserId(groupId, userId)
                .orElse(new Balance(groupId, userId, BigDecimal.ZERO));

        balance.add(delta);
        balanceRepo.save(balance);
        eventPublisher.publishEvent(
                new BalanceChangedInternalEvent(
                        groupId,
                        userId,
                        balance.getNetBalance()
                )
        );
    }


}
