package com.project.Splitwise.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.OutboxEvent;
import com.project.Splitwise.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Moves staged events from the outbox table to Kafka.
 *
 * <p>Delivery is at-least-once by design: the broker can accept a record and the relay can
 * die before marking the row published, in which case the next poll resends it. Consumers
 * deduplicate on {@code eventId}, so a duplicate is a no-op rather than a double-counted
 * balance.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Only event classes may be rehydrated — see {@link #resolveEventType}. */
    private static final String ALLOWED_EVENT_PACKAGE = "com.project.Splitwise.domain.event.";

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter published;
    private final Counter failed;
    private final SplitwiseMetrics metrics;

    @Value("${splitwise.outbox.batch-size:100}")
    private int batchSize;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry,
                       SplitwiseMetrics metrics) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.published = Counter.builder("splitwise.outbox.published")
                .description("Outbox events successfully handed to the broker")
                .register(meterRegistry);
        this.failed = Counter.builder("splitwise.outbox.failed")
                .description("Outbox publication attempts that errored")
                .register(meterRegistry);

        // Backlog depth is the signal that matters operationally: if this climbs, the
        // relay is losing to the write rate or the broker is unreachable.
        meterRegistry.gauge("splitwise.outbox.pending", repository,
                r -> (double) r.countByPublishedAtIsNull());

        // Depth alone cannot tell a healthy burst from a stuck queue. Age can: a backlog
        // that is large but young is the relay working through a spike, while one that is
        // small but old is a row nothing can publish.
        meterRegistry.gauge("splitwise.outbox.oldest.age.seconds", repository, r -> {
            Instant oldest = r.findOldestUnpublishedAt();
            return oldest == null ? 0d : (double) Duration.between(oldest, Instant.now()).toSeconds();
        });
    }

    @Scheduled(fixedDelayString = "${splitwise.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.lockNextUnpublished(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                Object payload = objectMapper.readValue(
                        event.getPayload(), resolveEventType(event.getEventType()));

                // Block on the ack. The row lock is held for the duration, which bounds
                // how far ahead of the broker the relay can get and keeps ordering per key.
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload)
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                event.markPublished();
                published.increment();
                // How long this row waited between being staged and reaching the broker.
                // Isolates relay latency from consumer latency when convergence lag spikes.
                metrics.recordOutboxPublishLag(event.getCreatedAt());
            } catch (Exception e) {
                // Leave published_at null so the next poll retries. Nothing is dropped.
                event.recordFailure(e.getMessage());
                failed.increment();
                log.warn("Outbox publish failed for {} (attempt {}): {}",
                        event.getId(), event.getAttempts(), e.toString());

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                // Stop the batch when the broker is the problem.
                //
                // If it was unreachable for this record it is unreachable for the rest, and
                // every one of them would burn the full send timeout finding that out. A
                // hundred-row batch against a down broker meant roughly seventeen minutes in
                // a single transaction, holding a hundred row locks and a pooled connection
                // the whole time. Abandoning the batch costs nothing: the rows are untouched,
                // and the next poll picks them up again.
                if (!isPerRecordFailure(e)) {
                    log.warn("Abandoning outbox batch after an infrastructure failure; "
                            + "{} events left for the next poll", remaining(batch, event));
                    break;
                }
                // Otherwise this row alone is bad — a payload that will not deserialise, or a
                // type that no longer exists. Skipping past it lets the healthy events behind
                // it through instead of queueing them behind something that can never succeed.
            }
        }

        repository.saveAll(batch);
    }

    /**
     * Whether a failure belongs to this row rather than to the broker.
     *
     * <p>The distinction decides whether one bad event stalls the queue behind it or the
     * relay gives up on a batch it cannot publish. A payload that will not deserialise, or a
     * type that no longer exists, is this row's problem and will fail identically forever —
     * so the batch continues past it. Anything else is assumed to be infrastructure.
     *
     * <p>Defaulting the unknown case to "infrastructure" is deliberate. Guessing wrong that
     * way costs one wasted poll; guessing the other way burns the whole batch's send timeouts
     * against a broker that is not there.
     */
    private static boolean isPerRecordFailure(Exception e) {
        return e instanceof JsonProcessingException
                || e instanceof ClassNotFoundException
                || e instanceof IllegalArgumentException;
    }

    private static int remaining(List<OutboxEvent> batch, OutboxEvent current) {
        return batch.size() - batch.indexOf(current) - 1;
    }

    /**
     * Rehydrating by class name off a database column is a deserialization sink, so the
     * name is constrained to the event package rather than passed to {@code Class.forName}
     * as-is.
     */
    private Class<?> resolveEventType(String eventType) throws ClassNotFoundException {
        if (eventType == null || !eventType.startsWith(ALLOWED_EVENT_PACKAGE)) {
            throw new IllegalArgumentException("Refusing to load non-event type: " + eventType);
        }
        return Class.forName(eventType);
    }
}
