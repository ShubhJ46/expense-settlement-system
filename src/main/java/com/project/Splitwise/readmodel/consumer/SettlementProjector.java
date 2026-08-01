package com.project.Splitwise.readmodel.consumer;

import com.project.Splitwise.domain.event.BalanceUpdatedEvent;
import com.project.Splitwise.domain.event.SettlementCalculatedEvent;
import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.domain.settlement.UserBalance;
import com.project.Splitwise.service.SettlementEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recomputes a group's settlement plan whenever its balances change and republishes it.
 *
 * <p>This closes a gap in the original design: {@code SettlementViewConsumer} listened on
 * {@code settlement-calculated}, but nothing in the codebase ever produced that topic, so
 * {@code settlement_view} stayed permanently empty and {@code GET /settlements/{groupId}}
 * always returned an empty list.
 */
@Component
public class SettlementProjector {

    private static final Logger log = LoggerFactory.getLogger(SettlementProjector.class);

    private final KafkaTemplate<String, SettlementCalculatedEvent> kafkaTemplate;

    public SettlementProjector(KafkaTemplate<String, SettlementCalculatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "balance-updated", groupId = "settlement-projector")
    public void onBalancesUpdated(BalanceUpdatedEvent event) {
        List<UserBalance> positions = event.getBalances().stream()
                .map(b -> new UserBalance(b.getUserId(), b.getNetBalance()))
                .toList();

        List<SettlementCalculatedEvent.Transfer> transfers =
                SettlementEngine.settle(positions).stream()
                        .map(SettlementProjector::toTransfer)
                        .toList();

        kafkaTemplate.send(
                "settlement-calculated",
                String.valueOf(event.getGroupId()),
                new SettlementCalculatedEvent(event.getGroupId(), transfers));

        log.debug("Recomputed {} transfer(s) for group {}", transfers.size(), event.getGroupId());
    }

    private static SettlementCalculatedEvent.Transfer toTransfer(Settlement settlement) {
        return new SettlementCalculatedEvent.Transfer(
                settlement.getFromUserId(),
                settlement.getToUserId(),
                settlement.getAmount());
    }
}
