package com.project.Splitwise.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


/**
 * Published when an expense is created; the balance consumer's input.
 *
 * <p>This is a wire contract, not an internal object. It is serialised into the outbox,
 * survives a process restart there, and is deserialised by a consumer that may be running a
 * different build of this code. Two consequences follow.
 *
 * <p>First, {@code @JsonIgnoreProperties(ignoreUnknown = true)}: a newer producer must be
 * able to add a field without breaking older consumers still draining the topic. Removing or
 * renaming a field is the breaking change to avoid.
 *
 * <p>Second, the event is self-contained. It carries the shares rather than an expense id
 * for the consumer to look up, so processing needs no read back to the write model and the
 * event means the same thing whenever it is replayed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class  ExpenseCreatedEvent {

    /**
     * The deduplication key. Generated once when the event is staged, so a relay retry
     * republishes the same id and the consumer recognises it as a duplicate rather than
     * applying the expense twice.
     */
    private String eventId;

    /**
     * When the event was staged, used to measure how long it takes to reach the read model.
     *
     * <p>Purely observational — nothing behaves differently because of it. Consumers written
     * against an older schema ignore it, and a replayed event that predates the field simply
     * contributes no timing sample.
     */
    private Instant occurredAt;

    private  Long expenseId;
    private  Long groupId;
    private  Long paidBy;
    private  BigDecimal amount;

    /** Must sum exactly to {@code amount}; the consumer rejects the event otherwise. */
    private  List<Share> shares;

    public record Share(Long userId, BigDecimal amount) {}
}
