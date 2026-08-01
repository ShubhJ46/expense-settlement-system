package com.project.Splitwise.domain.event;

/**
 * In-process signal that a group's balances were modified by the transaction currently
 * committing. Deliberately group-scoped rather than user-scoped: one expense touches
 * every participant, and publishing per-user would emit N near-identical full-group
 * snapshots to Kafka for a single expense.
 *
 * <p>Never leaves the JVM — {@link com.project.Splitwise.domain.event.BalanceUpdatedEvent}
 * is the published contract.
 */
public record GroupBalancesChangedEvent(Long groupId) {
}
