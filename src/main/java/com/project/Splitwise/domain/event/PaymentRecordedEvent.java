package com.project.Splitwise.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Emitted when a settlement payment is recorded. Carries its own {@code eventId} so the
 * consumer can deduplicate it exactly the way expense events are deduplicated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentRecordedEvent {

    private String eventId;

    /** When the event was staged; see {@link ExpenseCreatedEvent#getOccurredAt()}. */
    private Instant occurredAt;

    private Long paymentId;
    private Long groupId;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
}
