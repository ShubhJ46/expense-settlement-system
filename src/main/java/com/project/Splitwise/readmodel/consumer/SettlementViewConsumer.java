package com.project.Splitwise.readmodel.consumer;

import com.project.Splitwise.domain.event.SettlementCalculatedEvent;
import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SettlementViewConsumer {

    private final SettlementViewRepository repository;

    public SettlementViewConsumer(SettlementViewRepository repository) {
        this.repository = repository;
    }

    /**
     * Replaces the group's plan wholesale instead of appending.
     *
     * <p>{@code SettlementView} rows carry a generated id, so the previous append-only
     * {@code save()} produced a duplicate set of transfers on every balance change and on
     * every replay. Delete-then-insert inside one transaction makes the projection both
     * correct under replay and idempotent, which is what lets the consumer be safely
     * rewound to the start of the topic.
     */
    @KafkaListener(topics = "settlement-calculated", groupId = "query-service")
    @Transactional
    public void consume(SettlementCalculatedEvent event) {
        repository.deleteByGroupId(event.getGroupId());

        List<SettlementView> rows = event.getTransfers().stream()
                .map(t -> new SettlementView(
                        event.getGroupId(),
                        t.fromUserId(),
                        t.toUserId(),
                        t.amount()))
                .toList();

        repository.saveAll(rows);
    }
}
