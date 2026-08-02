package com.project.Splitwise.readmodel.consumer;

import com.project.Splitwise.domain.event.BalanceUpdatedEvent;
import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains the balance projection from the {@code balance-updated} snapshot stream.
 *
 * <p>The handler is an unconditional overwrite, and that is exactly what makes it safe under
 * at-least-once delivery: because the event carries a complete snapshot rather than a delta,
 * applying it twice produces the same rows as applying it once. There is no deduplication
 * here and none is needed — the operation is naturally idempotent.
 *
 * <p>Runs in its own consumer group, separate from the balance write path, so a slow or
 * failing projection cannot stall the authoritative balance updates.
 */
@Component
public class BalanceViewConsumer {

    private final GroupBalanceViewRepository repository;

    public BalanceViewConsumer(GroupBalanceViewRepository repository) {
        this.repository = repository;
    }

    /**
     * Applies one snapshot.
     *
     * <p>Transactional so a group's rows move together. A partially applied snapshot would
     * leave the projection showing a group whose balances do not sum to zero, which is
     * precisely the invariant a reader would use to sanity-check the data.
     */
    @KafkaListener(
            topics = "balance-updated",
            groupId = "query-service"
    )
    @Transactional
    public void consume(BalanceUpdatedEvent event) {
        Long groupId = event.getGroupId();

        for (BalanceUpdatedEvent.UserBalance ub : event.getBalances()) {
            // save() on an entity whose composite id already exists is a merge, so this
            // overwrites the previous figure rather than accumulating alongside it.
            repository.save(new GroupBalanceView(groupId, ub.getUserId(), ub.getNetBalance()));
        }
    }
}
