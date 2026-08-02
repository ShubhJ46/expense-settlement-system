package com.project.Splitwise.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A full snapshot of one group's balances, published after they change.
 *
 * <p>Deliberately a snapshot rather than a delta. A delta stream only converges if every
 * message is applied exactly once and in order; a snapshot is <em>self-correcting</em>,
 * because applying the latest one repairs the projection no matter what happened to the
 * messages before it. Duplicate delivery becomes a harmless overwrite rather than a
 * double-count.
 *
 * <p>The cost is message size — a large group ships every member's balance on every change.
 * That is the right trade here, where groups are small and correctness of the read model is
 * worth more than bytes on the wire.
 *
 * <p>Published keyed by {@code groupId}, so a group's snapshots share a partition and are
 * consumed in publication order. Unkeyed, they round-robin, and an older snapshot could
 * overwrite a newer one and move the read model backwards.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceUpdatedEvent {

    private Long groupId;

    /**
     * Carried through from the event that caused this change, so the projection can measure
     * the full write-to-read latency rather than only its own last hop.
     */
    private Instant occurredAt;

    /** Every member's net position at the moment the snapshot was taken, not just the changed ones. */
    private List<UserBalance> balances;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserBalance {
        private Long userId;
        private BigDecimal netBalance;
    }
}
