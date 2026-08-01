package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.GroupBalancesChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BalanceKafkaPublisher {

    private final BalanceEventProducer producer;

    public BalanceKafkaPublisher(BalanceEventProducer producer) {
        this.producer = producer;
    }

    /**
     * Publishes the group snapshot only after the balance transaction commits, so a
     * rolled-back transaction can never leak a balance update that never happened.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupBalancesChanged(GroupBalancesChangedEvent event) {
        producer.publish(event.groupId());
    }
}
