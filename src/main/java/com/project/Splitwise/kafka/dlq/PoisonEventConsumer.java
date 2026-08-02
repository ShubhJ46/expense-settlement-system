package com.project.Splitwise.kafka.dlq;

import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.PoisonMessage;
import com.project.Splitwise.readmodel.repository.PoisonMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Drains the dead-letter topics and preserves what landed there.
 *
 * <p>A DLT is an ordinary Kafka topic, so it has an ordinary retention window: a message
 * nobody looks at for a week is simply gone. Copying each one into {@code poison_messages}
 * means the evidence outlives the broker's retention and can be inspected — or replayed by
 * hand — long after the incident.
 *
 * <p>Every balance-mutating topic has to be listed here. A DLT with no consumer is
 * indistinguishable from no failures at all, which is the worst possible way to find out
 * about a bug.
 */
@Slf4j
@Component
public class PoisonEventConsumer {

    private final PoisonMessageRepository poisonRepo;
    private final SplitwiseMetrics metrics;

    public PoisonEventConsumer(PoisonMessageRepository poisonRepo, SplitwiseMetrics metrics) {
        this.poisonRepo = poisonRepo;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = {"expense-created.DLT", "payment-recorded.DLT"},
            groupId = "dlq-service"
    )
    public void consumePoisonMessage(
            ConsumerRecord<String, Object> record,
            // Spring's DeadLetterPublishingRecoverer stamps the original failure onto the
            // record as headers. Reading them preserves the actual cause instead of storing
            // a fixed string that says nothing about what went wrong.
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic) {

        log.error("Poison message on {}: partition={} offset={} cause={}",
                record.topic(), record.partition(), record.offset(), exceptionMessage);

        PoisonMessage poison = new PoisonMessage(
                // Prefer the original topic, so the stored row points at where the message
                // came from rather than at the DLT it ended up on.
                originalTopic != null ? originalTopic : record.topic(),
                record.partition(),
                record.offset(),
                String.valueOf(record.value()),
                exceptionMessage != null ? exceptionMessage : "Failed after max retries"
        );

        poisonRepo.save(poison);

        // Alertable. A log line is only found by someone already looking; a counter going
        // non-zero is what actually pages.
        metrics.poisoned(originalTopic != null ? originalTopic : record.topic());
    }
}
