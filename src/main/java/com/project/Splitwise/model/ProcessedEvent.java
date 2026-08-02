package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The deduplication ledger: one row per event this service has already applied.
 *
 * <p>This single table is what turns Kafka's at-least-once delivery into effectively-once
 * processing. The trick is not the table itself but <em>when</em> it is written: the row is
 * inserted in the same transaction as the balance mutations it guards, so the two either
 * both happen or neither does. A consumer that crashes after the DB commit but before the
 * offset commit gets the record again, finds the row, and does nothing.
 *
 * <p>The event id is the primary key rather than a surrogate, because the id <em>is</em> the
 * identity being asserted and a unique index on it is the actual guarantee.
 *
 * <p>Known wart: nothing prunes this table, so it grows for the lifetime of the system. A
 * real deployment would age rows out past the broker's retention window, beyond which a
 * replay is impossible anyway.
 */
@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id")
)
@NoArgsConstructor
public class ProcessedEvent {

    /** The producer-assigned event id, generated once at the source and stable across retries. */
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
