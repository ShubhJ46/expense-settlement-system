package com.project.Splitwise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A domain event durably staged in the same database transaction as the state change that
 * produced it.
 *
 * <p>This exists to remove a dual write. Previously {@code ExpenseService} saved the
 * expense and called Kafka inside one method, which is two systems and no shared
 * transaction: a broker timeout after the DB commit silently lost the event, and a DB
 * rollback after a successful send published an expense that does not exist. Writing the
 * event as a row makes the commit atomic; a relay moves it to Kafka afterwards.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    /** Fully-qualified event class, used by the relay to rehydrate the payload. */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(name = "message_key", length = 255)
    private String messageKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null until the relay has confirmed the broker accepted it. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType,
                       String aggregateId,
                       String eventType,
                       String topic,
                       String messageKey,
                       String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    /** When the row was staged. Read by the relay to report how long publication waited. */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
