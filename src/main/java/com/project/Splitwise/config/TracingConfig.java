package com.project.Splitwise.config;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares how trace context crosses a process boundary.
 *
 * <p>This is stated explicitly because leaving it to auto-configuration produced a
 * {@code ContextPropagators} that propagated nothing — {@code fields()} empty, an inbound
 * {@code traceparent} ignored, and nothing written on the way out. The failure is entirely
 * silent: spans are still created, still sampled, still exported, and every trace is a
 * disconnected fragment of one request. Nothing logs a warning, because from the SDK's point
 * of view propagating no fields is a valid configuration.
 *
 * <p>Defining the bean removes the guesswork. W3C is the standard {@code traceparent} format,
 * and it is also what {@link com.project.Splitwise.outbox.OutboxTracing} stores in the outbox
 * row, so the format on the wire and the format in the database are necessarily the same one.
 */
@Configuration
public class TracingConfig {

    @Bean
    public ContextPropagators contextPropagators() {
        return ContextPropagators.create(TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                // Baggage travels with the trace. Nothing sets any today, but including it
                // means adding one later is a code change rather than a config surprise.
                W3CBaggagePropagator.getInstance()));
    }
}
