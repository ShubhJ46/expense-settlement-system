package com.project.Splitwise.kafka;

import com.project.Splitwise.repository.BalanceRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BalanceEventProducer {
    private final KafkaTemplate<String, BalanceUpdatedEvent> kafkaTemplate;
    private final BalanceRepository balanceRepo;

    public BalanceEventProducer(KafkaTemplate<String, BalanceUpdatedEvent> kafkaTemplate, BalanceRepository balanceRepo) {
        this.kafkaTemplate = kafkaTemplate;
        this.balanceRepo = balanceRepo;
    }

    public void publish(Long groupId) {

        List<BalanceUpdatedEvent.UserBalance> balances =
                balanceRepo.findAll().stream()
                        .filter(b -> b.getGroupId().equals(groupId))
                        .map(b -> new BalanceUpdatedEvent.UserBalance(
                                b.getUserId(),
                                b.getNetBalance()
                        ))
                        .toList();


        BalanceUpdatedEvent event = new BalanceUpdatedEvent(groupId, balances);
        kafkaTemplate.send("balance-updated", event);

    }
}
