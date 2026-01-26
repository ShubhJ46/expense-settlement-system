package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.repository.ProcessedEventRepository;
import com.project.Splitwise.service.BalanceService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;

import org.apache.kafka.clients.consumer.Consumer;

import java.util.List;
import java.util.Map;

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
    public void consume(
            ExpenseCreatedEvent event,
            ConsumerRecord<String, ExpenseCreatedEvent> record,
            Consumer<String, ExpenseCreatedEvent> consumer,
            Acknowledgment ack
    ) {
        log.info("CONSUMED EVENT: {}", event.getExpenseId());


        TopicPartition tp =
                new TopicPartition(record.topic(), record.partition());

        long currentOffset = record.offset();

        Map<TopicPartition, Long> endOffsets =
                consumer.endOffsets(List.of(tp));

        long endOffset = endOffsets.get(tp);

        long lag = endOffset - currentOffset;

        if (lag > 100) {
            log.warn(
                    "High consumer lag detected | topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
        }


        if (event.getAmount() == null || event.getAmount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Invalid amount in ExpenseCreatedEvent: " + event
            );
        }

        balanceService.handleExpense(event);
        ack.acknowledge();
    }


}
