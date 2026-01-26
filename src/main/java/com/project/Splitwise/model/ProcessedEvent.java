package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id")
)
@NoArgsConstructor
public class ProcessedEvent {
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
