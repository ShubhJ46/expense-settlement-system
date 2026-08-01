package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.BalanceUpdatedEvent;
import com.project.Splitwise.repository.BalanceRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class BalanceEventProducer {

    private final KafkaTemplate<String, BalanceUpdatedEvent> kafkaTemplate;
    private final BalanceRepository balanceRepo;

    public BalanceEventProducer(KafkaTemplate<String, BalanceUpdatedEvent> kafkaTemplate,
                                BalanceRepository balanceRepo) {
        this.kafkaTemplate = kafkaTemplate;
        this.balanceRepo = balanceRepo;
    }

    @Transactional(readOnly = true)
    public void publish(Long groupId) {
        List<BalanceUpdatedEvent.UserBalance> balances =
                balanceRepo.findByGroupId(groupId).stream()
                        .map(b -> new BalanceUpdatedEvent.UserBalance(
                                b.getUserId(),
                                b.getNetBalance()))
                        .toList();

        BalanceUpdatedEvent event = new BalanceUpdatedEvent(groupId, balances);

        // Keyed by groupId. This event carries a full-group snapshot, so two unkeyed
        // sends could land on different partitions and be applied out of order by the
        // projector, letting the read model move backwards in time. Keying pins every
        // snapshot for a group to one partition, where offset order is total order.
        kafkaTemplate.send("balance-updated", String.valueOf(groupId), event);
    }
}
