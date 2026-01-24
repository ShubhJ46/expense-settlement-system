package com.project.Splitwise.kafka;

import com.project.Splitwise.domain.event.BalanceChangedInternalEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BalanceKafkaPublisher {
    private final BalanceEventProducer producer;


    public BalanceKafkaPublisher(BalanceEventProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalanceChanged(BalanceChangedInternalEvent event) {



        producer.publish(
                event.getGroupId()
        );
    }
}
