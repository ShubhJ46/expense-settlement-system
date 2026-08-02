package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An event that could not be processed and was routed to the dead-letter topic.
 *
 * <p>Persisting these matters because a DLT is itself a Kafka topic with a retention window:
 * left alone, the evidence of a failure expires before anyone investigates it. A row here
 * outlives the broker's retention and carries enough context — topic, partition, offset, the
 * original payload and the exception — to replay the message by hand once the cause is fixed.
 *
 * <p>{@code kafkaOffset} and {@code kafkaPartition} are named around {@code offset} and
 * {@code partition} deliberately: both are reserved words in SQL and would need quoting.
 */
@Entity
@Table(name = "poison_messages")
@Getter
public class PoisonMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    /** The original record, verbatim, so the message can be reconstructed and replayed. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    /** Why it failed. TEXT rather than varchar because stack traces overrun any sane limit. */
    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime failedAt;

    protected PoisonMessage() {}

    public PoisonMessage(
            String topic,
            Integer partition,
            Long offset,
            String payload,
            String error
    ) {
        this.topic = topic;
        this.kafkaPartition = partition;
        this.kafkaOffset = offset;
        this.payload = payload;
        this.error = error;
        this.failedAt = LocalDateTime.now();
    }
}

