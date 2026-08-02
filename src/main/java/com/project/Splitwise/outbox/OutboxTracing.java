package com.project.Splitwise.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries trace context across the outbox.
 *
 * <p>The outbox is a deliberate discontinuity: the HTTP thread commits a row and returns, and
 * some time later a scheduled poll on a different thread publishes it. That break is the
 * whole point of the pattern, and it severs a distributed trace exactly the way it severs a
 * stack trace. Left alone, every relay publish starts its own trace, and the question anyone
 * actually asks — <em>where did this expense spend its time between the API call and the
 * balance appearing?</em> — cannot be answered, because the two halves are unrelated traces.
 *
 * <p>So the context travels in the row. The staging side writes a W3C {@code traceparent};
 * the relay reads it back and starts its span as a child of the original request. Kafka
 * headers carry it onward from there, which Spring's own instrumentation handles, so the
 * consumer and the projection continue the same trace.
 *
 * <p>Everything here degrades to a no-op when nothing is sampled. Under any sampling rate
 * below one that is the common case, not an error.
 */
@Component
public class OutboxTracing {

    /** The W3C header name; the propagator writes it into whatever carrier it is given. */
    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTracing(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /**
     * Serialises the current trace context, or null if there is nothing to carry.
     *
     * <p>Called on the request thread, inside the transaction that stages the event, so the
     * context captured is the one belonging to the API call that caused it.
     */
    public String currentTraceParent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /**
     * Starts a span for publishing this row, parented onto the request that staged it.
     *
     * <p>Returns a span either way. With no stored context this is simply the root of a new
     * trace covering the publish, which is still worth having — it just cannot be linked back
     * to an API call that was never sampled.
     */
    public Span startPublishSpan(String traceParent, String topic) {
        Span.Builder builder = (traceParent == null || traceParent.isBlank())
                ? tracer.spanBuilder()
                : propagator.extract(Map.of(TRACEPARENT, traceParent), Map::get);

        return builder
                .name("outbox publish")
                .tag("messaging.system", "kafka")
                .tag("messaging.destination.name", topic)
                .kind(Span.Kind.PRODUCER)
                .start();
    }

    /**
     * Makes {@code span} current for the duration of {@code work}.
     *
     * <p>The scope matters as much as the span: Spring's Kafka instrumentation reads the
     * <em>current</em> context to decide what its own send span should descend from, so a
     * span that is started but never made current produces a correctly-parented publish span
     * with an orphaned Kafka span beside it.
     */
    public void inScope(Span span, Runnable work) {
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            work.run();
        }
    }
}
