package com.project.Splitwise.kafka;

import com.project.Splitwise.controller.ExpenseController;
import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.model.Expense;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@Component
@Slf4j
public class ExpenseEventProducer {
    private static final Logger log = LoggerFactory.getLogger(ExpenseEventProducer.class);
    private final KafkaTemplate<String, ExpenseCreatedEvent> kafkaTemplate;

    public ExpenseEventProducer(KafkaTemplate<String, ExpenseCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ExpenseCreatedEvent event) {

        if (event.getGroupId() == null) {
            throw new IllegalStateException("groupId cannot be null");
        }

        kafkaTemplate.send(
                "expense-created",
                event.getGroupId().toString(),
                event
        );

        log.info("Publishing ExpenseCreatedEvent for expenseId={}", event.getExpenseId());

    }

}
