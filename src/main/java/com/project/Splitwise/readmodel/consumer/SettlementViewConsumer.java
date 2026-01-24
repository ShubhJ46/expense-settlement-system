package com.project.Splitwise.readmodel.consumer;

import com.project.Splitwise.domain.event.SettlementCalculatedEvent;
import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SettlementViewConsumer {

    private final SettlementViewRepository repository;

    public SettlementViewConsumer(SettlementViewRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(
            topics = "settlement-calculated",
            groupId = "query-service"
    )
    public void consume(SettlementCalculatedEvent event) {

        SettlementView view = new SettlementView(
                event.getGroupId(),
                event.getFromUserId(),
                event.getToUserId(),
                event.getAmount()
        );

        repository.save(view);
    }
}
