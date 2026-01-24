package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.repository.ProcessedEventRepository;
import com.project.Splitwise.service.BalanceService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class BalanceEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(BalanceEventConsumer.class);
    private final BalanceService balanceService;
    private final ProcessedEventRepository processedEventRepo;
    public BalanceEventConsumer (BalanceService balanceService, ProcessedEventRepository processedEventRepo) {
        this.balanceService = balanceService;
        this.processedEventRepo = processedEventRepo;
    }

    @KafkaListener(
            topics = "expense-created",
            groupId = "balance-service"
    )
    @Transactional
    public void consume(ExpenseCreatedEvent event) {
        log.info("CONSUMED EVENT: {}", event.getExpenseId());

        /*// Controlled poison simulation
        if (event.getGroupId() == 999L) {
            throw new RuntimeException("Simulated consumer failure");
        }*/

        balanceService.handleExpense(event);
    }


}
