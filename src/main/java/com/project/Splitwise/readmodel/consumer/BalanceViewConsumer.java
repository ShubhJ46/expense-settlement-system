package com.project.Splitwise.readmodel.consumer;

import com.project.Splitwise.domain.event.BalanceUpdatedEvent;
import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BalanceViewConsumer {

    private final GroupBalanceViewRepository repository;

    public BalanceViewConsumer(GroupBalanceViewRepository repository) {
        this.repository = repository;
    }

    @KafkaListener (
        topics = "balance-updated",
        groupId = "query-service"
    )
    public void consume(BalanceUpdatedEvent event) {
        Long groupId = event.getGroupId();

        /*//For testing error handling
        if (groupId == 999L) {
            throw new IllegalArgumentException("Invalid group");
        }*/

        for(BalanceUpdatedEvent.UserBalance ub : event.getBalances()) {
            GroupBalanceView view = new GroupBalanceView(
                    groupId,
                    ub.getUserId(),
                    ub.getNetBalance()
            );
            repository.save(view);
        }

    }

}
