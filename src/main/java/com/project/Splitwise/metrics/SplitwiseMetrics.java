package com.project.Splitwise.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Every custom meter in the service, declared in one place.
 *
 * <p>Metric names are an API: dashboards and alerts are written against them, so a rename is
 * a breaking change for whoever is on call. Registering them centrally rather than inline at
 * each call site means the whole vocabulary can be reviewed on one screen, and it keeps the
 * naming consistent — {@code splitwise.<area>.<thing>}, dot-separated, which Micrometer
 * translates into the right convention per backend.
 *
 * <p>Deliberately absent: anything Spring Boot already binds. HTTP latency, JVM memory,
 * connection pool usage and Kafka client lag are all auto-instrumented, and re-registering
 * them by hand would only produce a second, subtly different set of numbers.
 */
@Component
public class SplitwiseMetrics {

    private final MeterRegistry registry;

    private final Timer convergenceLag;
    private final Timer outboxPublishLag;
    private final Counter eventsApplied;
    private final Counter eventsDeduplicated;
    private final Counter lockConflicts;
    private final Counter expensesCreated;
    private final Counter paymentsRecorded;

    public SplitwiseMetrics(MeterRegistry registry) {
        this.registry = registry;

        // The headline number for an eventually-consistent system: how long after a write
        // does a reader actually see it. Percentiles rather than a mean, because the tail is
        // what a user notices and what an SLO is written against.
        this.convergenceLag = Timer.builder("splitwise.convergence.lag")
                .description("Time from an event being staged to the balance projection reflecting it")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Splits the above: how much of the lag is the relay waiting to pick the row up,
        // as opposed to consumers processing it. Without this, a lag spike is unattributable.
        this.outboxPublishLag = Timer.builder("splitwise.outbox.publish.lag")
                .description("Time from an event being staged in the outbox to being accepted by the broker")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.eventsApplied = Counter.builder("splitwise.events.processed")
                .tag("outcome", "applied")
                .description("Events that changed balances")
                .register(registry);

        // The ratio of this to `applied` is the redelivery rate. A healthy system shows a
        // trickle; a spike means the consumer is failing after its DB commit but before its
        // offset commit, which is invisible in any success metric.
        this.eventsDeduplicated = Counter.builder("splitwise.events.processed")
                .tag("outcome", "duplicate")
                .description("Events recognised as already applied and skipped")
                .register(registry);

        // Evidence that the optimistic lock on Balance is doing something. Expenses and
        // payments write the same rows from different consumers, and this is the only
        // signal that the two ever actually collide in production.
        this.lockConflicts = Counter.builder("splitwise.balance.lock.conflicts")
                .description("Optimistic lock failures on a balance row, each retried by the error handler")
                .register(registry);

        // "accepted", not "created", and the distinction is not stylistic. OpenMetrics
        // reserves the `_created` suffix for a series' creation timestamp, so a meter named
        // splitwise.expenses.created is silently rewritten to splitwise_expenses_total on
        // scrape — a name nothing queries and nobody would think to look for.
        this.expensesCreated = Counter.builder("splitwise.expenses.accepted")
                .description("Expenses accepted over HTTP")
                .register(registry);

        this.paymentsRecorded = Counter.builder("splitwise.payments.recorded")
                .description("Settlement payments accepted over HTTP")
                .register(registry);
    }

    /**
     * Records how long an event took to reach the read model.
     *
     * <p>Silently skipped when {@code occurredAt} is absent, which happens for events staged
     * by an older build still draining from a topic. A missing sample is better than a
     * fabricated one.
     *
     * <p>Single-JVM here, so the two clock readings are consistent. Across nodes this
     * measures clock skew as well as real latency — worth knowing before trusting it in a
     * distributed deployment.
     */
    public void recordConvergence(Instant occurredAt) {
        if (occurredAt == null) {
            return;
        }
        Duration elapsed = Duration.between(occurredAt, Instant.now());
        if (!elapsed.isNegative()) {
            convergenceLag.record(elapsed);
        }
    }

    public void recordOutboxPublishLag(Instant stagedAt) {
        if (stagedAt == null) {
            return;
        }
        Duration elapsed = Duration.between(stagedAt, Instant.now());
        if (!elapsed.isNegative()) {
            outboxPublishLag.record(elapsed);
        }
    }

    public void eventApplied() {
        eventsApplied.increment();
    }

    public void eventDeduplicated() {
        eventsDeduplicated.increment();
    }

    public void balanceLockConflict() {
        lockConflicts.increment();
    }

    public void expenseCreated() {
        expensesCreated.increment();
    }

    public void paymentRecorded() {
        paymentsRecorded.increment();
    }

    /**
     * Counts a message that reached a dead-letter topic, tagged by where it came from.
     *
     * <p>Built on demand rather than in the constructor because the tag value is only known
     * at failure time; Micrometer returns the existing meter for a repeated name/tag pair, so
     * this does not leak registrations.
     */
    public void poisoned(String topic) {
        Counter.builder("splitwise.events.poisoned")
                .tag("topic", topic == null ? "unknown" : topic)
                .description("Messages routed to a dead-letter topic and persisted")
                .register(registry)
                .increment();
    }
}
