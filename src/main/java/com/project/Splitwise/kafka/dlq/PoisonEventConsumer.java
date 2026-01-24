package com.project.Splitwise.kafka.dlq;

import com.project.Splitwise.model.PoisonMessage;
import com.project.Splitwise.readmodel.repository.PoisonMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PoisonEventConsumer {
    private final PoisonMessageRepository poisonRepo;

    public PoisonEventConsumer(PoisonMessageRepository poisonRepo) {
        this.poisonRepo = poisonRepo;
    }

    @KafkaListener(
            topics = "expense-created.DLT",
            groupId = "dlq-service"
    )
    public void consumePoisonMessage(
            ConsumerRecord<String, Object> record) {

        log.error("🚨 POISON MESSAGE RECEIVED from DLQ");
        log.error("Payload: {}", record.value());

        PoisonMessage poison = new PoisonMessage(
                record.topic(),
                record.partition(),
                record.offset(),
                String.valueOf(record.value()),
                "Failed after max retries"
        );

        poisonRepo.save(poison);
    }
}
