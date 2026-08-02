package com.project.Splitwise.domain.event;

/**
 * In-process signal that a group's balances were modified by the transaction currently
 * committing. Deliberately group-scoped rather than user-scoped: one expense touches
 * every participant, and publishing per-user would emit N near-identical full-group
 * snapshots to Kafka for a single expense.
 *
 * <p>Never leaves the JVM — {@link com.project.Splitwise.domain.event.BalanceUpdatedEvent}
 * is the published contract.
 *
 * @param occurredAt when the originating event was staged, forwarded so the projection can
 *                   report end-to-end convergence latency instead of losing the start time
 *                   at this hop.
 */
public record GroupBalancesChangedEvent(Long groupId, java.time.Instant occurredAt) {
}
