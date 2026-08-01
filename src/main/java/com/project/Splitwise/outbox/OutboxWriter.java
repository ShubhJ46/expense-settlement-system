package com.project.Splitwise.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Splitwise.model.OutboxEvent;
import com.project.Splitwise.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Stages an event for publication. Must be called inside the same transaction as the
     * state change it describes — that shared transaction is the entire point.
     */
    public void append(String aggregateType,
                       String aggregateId,
                       String topic,
                       String messageKey,
                       Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Serialization failure must abort the business transaction: committing the
            // expense without its event is precisely the inconsistency the outbox prevents.
            throw new IllegalStateException(
                    "Could not serialize outbox payload for " + event.getClass().getName(), e);
        }

        repository.save(new OutboxEvent(
                aggregateType,
                aggregateId,
                event.getClass().getName(),
                topic,
                messageKey,
                payload));
    }
}
