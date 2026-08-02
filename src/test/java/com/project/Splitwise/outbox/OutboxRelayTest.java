package com.project.Splitwise.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.Splitwise.domain.event.ExpenseCreatedEvent;
import com.project.Splitwise.metrics.SplitwiseMetrics;
import com.project.Splitwise.model.OutboxEvent;
import com.project.Splitwise.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers how the relay reacts to a failed publish, which is the difference between a batch
 * that gives up in one send timeout and one that burns the timeout once per row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OutboxRelayTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OutboxRelay relay;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, objectMapper,
                new SimpleMeterRegistry(), new SplitwiseMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(relay, "batchSize", 100);
    }

    private OutboxEvent stagedEvent() {
        ExpenseCreatedEvent event = ExpenseCreatedEvent.builder()
                .eventId("evt-" + System.nanoTime())
                .expenseId(1L).groupId(7L).paidBy(1L)
                .amount(new BigDecimal("10.00"))
                .shares(List.of(new ExpenseCreatedEvent.Share(1L, new BigDecimal("10.00"))))
                .build();
        try {
            return new OutboxEvent("Expense", "1", ExpenseCreatedEvent.class.getName(),
                    "expense-created", "7", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A row whose declared type is not loadable — broken in itself, not because of the broker. */
    private OutboxEvent undeserialisableEvent() {
        return new OutboxEvent("Expense", "2",
                "com.project.Splitwise.domain.event.NoSuchEvent",
                "expense-created", "7", "{}");
    }

    private static CompletableFuture<Object> brokerDown() {
        return CompletableFuture.failedFuture(
                new org.apache.kafka.common.errors.TimeoutException("no broker available"));
    }

    @Test
    @DisplayName("a broker failure abandons the rest of the batch instead of timing out on each row")
    void brokerFailureAbandonsTheBatch() {
        when(repository.lockNextUnpublished(anyInt()))
                .thenReturn(List.of(stagedEvent(), stagedEvent(), stagedEvent(), stagedEvent()));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(inv -> brokerDown());

        relay.publishPending();

        // The whole point: one attempt, not four. Against a real broker each of those would
        // have blocked for the full send timeout before reaching the same conclusion.
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("nothing is marked published when the broker is down, so the next poll retries")
    void failedBatchStaysUnpublished() {
        List<OutboxEvent> batch = List.of(stagedEvent(), stagedEvent());
        when(repository.lockNextUnpublished(anyInt())).thenReturn(batch);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(inv -> brokerDown());

        relay.publishPending();

        assertEquals(0, batch.stream().filter(e -> e.getPublishedAt() != null).count(),
                "a failed publish must leave the row for the next poll");
        assertEquals(1, batch.get(0).getAttempts(), "the attempted row records its failure");
        assertEquals(0, batch.get(1).getAttempts(), "the abandoned row was never attempted");
        verify(repository).saveAll(batch);
    }

    @Test
    @DisplayName("one unpublishable row does not stall the healthy events behind it")
    void perRecordFailureDoesNotAbandonTheBatch() {
        List<OutboxEvent> batch = List.of(undeserialisableEvent(), stagedEvent(), stagedEvent());
        when(repository.lockNextUnpublished(anyInt())).thenReturn(batch);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(null));

        relay.publishPending();

        // The bad row can never succeed, so treating it as a reason to stop would block the
        // queue behind it forever. The two healthy events go out.
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
        assertEquals(2, batch.stream().filter(e -> e.getPublishedAt() != null).count());
        assertEquals(1, batch.get(0).getAttempts(), "the bad row records a failed attempt");
    }

    @Test
    @DisplayName("a healthy batch publishes every row")
    void healthyBatchPublishesEverything() {
        List<OutboxEvent> batch = List.of(stagedEvent(), stagedEvent(), stagedEvent());
        when(repository.lockNextUnpublished(anyInt())).thenReturn(batch);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenAnswer(inv -> CompletableFuture.completedFuture(null));

        relay.publishPending();

        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), any());
        assertEquals(3, batch.stream().filter(e -> e.getPublishedAt() != null).count());
    }

    @Test
    void emptyBatchDoesNothing() {
        when(repository.lockNextUnpublished(anyInt())).thenReturn(List.of());

        relay.publishPending();

        verify(kafkaTemplate, times(0)).send(anyString(), anyString(), any());
        verify(repository, times(0)).saveAll(any());
    }
}
