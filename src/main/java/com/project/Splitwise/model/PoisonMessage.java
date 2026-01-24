package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "poison_messages")
@Getter
public class PoisonMessage {

    @Id
    @GeneratedValue
    private Long id;

    private String topic;
    @Column(name = "kafka_offset")
    private Long kafkaOffset;
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;



    @Column(columnDefinition = "TEXT")
    private String payload;

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

