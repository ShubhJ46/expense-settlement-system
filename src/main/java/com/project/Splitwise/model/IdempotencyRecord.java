package com.project.Splitwise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Remembers that a client already made this request, and what it produced.
 *
 * <p>The service goes to considerable trouble to apply each event exactly once between the
 * database and Kafka, and none of that helps at the edge: a client whose {@code POST} times
 * out and retries would create a second, entirely valid expense. This table closes that gap,
 * and closes it the same way the consumer does — the record is written in the <em>same
 * transaction</em> as the expense it describes, so a retry either finds both or neither.
 *
 * <p>Keyed by (key, user) rather than key alone. A client's idempotency keys are its own
 * namespace: without the user in the key, one caller could observe — or collide with —
 * another caller's key, which turns a reliability feature into an information leak.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyRecordId.class)
@Getter
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Id
    private Long userId;

    /** What was created, so a replay can return the original rather than repeat the work. */
    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private Long resourceId;

    /**
     * Hash of the request body.
     *
     * <p>Reusing one key for a different payload is a client bug, not a retry, and returning
     * the first response would silently discard the second request. Stored so that case can
     * be detected and refused rather than guessed at.
     */
    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, Long userId, String resourceType,
                             Long resourceId, String requestFingerprint) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.requestFingerprint = requestFingerprint;
    }
}
