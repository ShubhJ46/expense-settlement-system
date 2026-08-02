package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.model.OutboxEvent;
import com.project.Splitwise.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that a trace survives the outbox.
 *
 * <p>The outbox deliberately breaks the call chain, and that break severs a distributed trace
 * the same way it severs a stack trace: the request thread is long gone by the time a
 * scheduler thread publishes the row. Unless the context is carried in the row itself, the
 * publish begins a brand new trace and the API call it belongs to cannot be found from it.
 *
 * <p>The test supplies its own {@code traceparent} on the request rather than trying to read
 * one back out of the server. That makes the assertion exact: whatever trace id the caller
 * arrived with must be the trace id sitting in the outbox row afterwards, with no guessing
 * about which trace the server happened to create.
 */
class TracingIT extends AbstractIntegrationTest {

    /** A syntactically valid W3C traceparent: version-traceid-spanid-flags, sampled. */
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT =
            "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

    @Autowired
    private OutboxEventRepository outboxEvents;

    private TestUser payer;
    private TestUser other;
    private Long groupId;

    @BeforeEach
    void setUpGroup() {
        payer = registerUser();
        other = registerUser();
        groupId = createGroup(payer, other);
    }

    private CreateExpenseRequest expense(String amount) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(List.of(payer.id(), other.id()));
        return req;
    }

    /** Finds the staged row for this group, published or not. */
    private Optional<OutboxEvent> outboxRowForGroup() {
        return outboxEvents.findAll().stream()
                .filter(e -> String.valueOf(groupId).equals(e.getMessageKey()))
                .findFirst();
    }

    @Test
    @DisplayName("the caller's trace id is carried into the outbox row")
    void traceContextCrossesTheOutbox() {
        HttpHeaders headers = authHeaders(payer);
        headers.set("traceparent", TRACEPARENT);

        assertEquals(HttpStatus.CREATED, restTemplate.exchange("/expenses", HttpMethod.POST,
                new HttpEntity<>(expense("100.00"), headers), String.class).getStatusCode());

        OutboxEvent staged = await().atMost(Duration.ofSeconds(30))
                .until(this::outboxRowForGroup, Optional::isPresent)
                .orElseThrow();

        String carried = staged.getTraceParent();
        assertTrue(carried != null && !carried.isBlank(),
                "the staged row should carry the request's trace context");

        // Same trace, different span: the relay's publish is a child of the API call, not a
        // continuation of the exact same span.
        assertTrue(carried.contains(TRACE_ID),
                "expected the caller's trace id " + TRACE_ID + " but the row carried " + carried);

        // W3C format, since the relay has to hand it back to a propagator that will reject
        // anything malformed — and would do so on a scheduler thread where nobody is looking.
        assertTrue(carried.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"),
                "traceparent is not well-formed: " + carried);
    }

    @Test
    @DisplayName("a request with no trace context still publishes")
    void untracedRequestsStillWork() {
        // Under any sampling rate below 1.0 most requests look like this, so an absent
        // context has to be ordinary rather than an error path.
        assertEquals(HttpStatus.CREATED, restTemplate.exchange("/expenses", HttpMethod.POST,
                as(payer, expense("60.00")), String.class).getStatusCode());

        // The row drains regardless of whether anything was traced.
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                assertEquals(0, outboxEvents.countByPublishedAtIsNull(),
                        "an untraced event must still publish"));
    }

    @Test
    @DisplayName("the row keeps its trace context through to publication")
    void tracedRowPublishesSuccessfully() {
        HttpHeaders headers = authHeaders(payer);
        headers.set("traceparent", TRACEPARENT);

        restTemplate.exchange("/expenses", HttpMethod.POST,
                new HttpEntity<>(expense("40.00"), headers), String.class);

        // The relay reads the stored context back through a propagator to parent its span.
        // If that round trip threw, the row would never be marked published — so a drained
        // outbox is also the assertion that the stored value was usable.
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() ->
                assertEquals(0, outboxEvents.countByPublishedAtIsNull(),
                        "a traced event should publish like any other"));

        assertTrue(outboxRowForGroup().orElseThrow().getTraceParent().contains(TRACE_ID),
                "publication should not have erased the trace context");
    }
}
