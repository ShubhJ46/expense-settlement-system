package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the metrics are real: registered, incremented by actual traffic, and reachable.
 *
 * <p>A metric that exists but never moves is worse than none, because a dashboard built on
 * it looks healthy while measuring nothing. Each assertion here drives real traffic through
 * the system and then reads the meter back.
 */
class MetricsIT extends AbstractIntegrationTest {

    @Autowired
    private MeterRegistry meterRegistry;

    private double counter(String name) {
        var found = meterRegistry.find(name).counter();
        return found == null ? 0d : found.count();
    }

    @Test
    @DisplayName("an expense moves the business counters and lands a convergence sample")
    void trafficMovesTheMeters() {
        TestUser payer = registerUser();
        TestUser other = registerUser();
        Long groupId = createGroup(payer, other);

        double before = counter("splitwise.expenses.accepted");

        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal("100.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(List.of(payer.id(), other.id()));

        restTemplate.exchange("/expenses", HttpMethod.POST, as(payer, req), String.class);

        assertTrue(counter("splitwise.expenses.accepted") > before,
                "creating an expense should increment the counter");

        // The convergence timer only records once the projection has caught up, so this
        // waits rather than asserting immediately — the same eventual consistency the
        // metric exists to measure.
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            var timer = meterRegistry.find("splitwise.convergence.lag").timer();
            assertTrue(timer != null && timer.count() > 0,
                    "the projection should have recorded a convergence sample");
            assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0,
                    "convergence lag should be a positive duration");
        });

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertTrue(counter("splitwise.events.processed") > 0,
                        "the balance consumer should have counted an applied event"));
    }

    @Test
    @DisplayName("the outbox gauges are registered and readable")
    void outboxGaugesExist() {
        assertTrue(meterRegistry.find("splitwise.outbox.pending").gauge() != null,
                "pending depth gauge should be registered");

        var age = meterRegistry.find("splitwise.outbox.oldest.age.seconds").gauge();
        assertTrue(age != null, "oldest-age gauge should be registered");

        // Reading it must not throw on an empty outbox — the underlying MIN() returns null,
        // and a gauge that blows up on the quiet path is the one that breaks during an
        // incident when nothing else is happening.
        assertTrue(age.value() >= 0, "age gauge should read non-negative, got " + age.value());
    }

    @Test
    @DisplayName("the Prometheus registry is the active one, not a silent Simple fallback")
    void prometheusRegistryIsConfigured() {
        // Regression guard. Declaring micrometer-registry-prometheus and exposing the
        // endpoint is not enough: without management.prometheus.metrics.export.enabled the
        // context falls back to a SimpleMeterRegistry, meters are collected normally, and
        // only the scrape endpoint is missing — a failure invisible from inside the app.
        assertTrue(meterRegistry instanceof PrometheusMeterRegistry,
                "expected a PrometheusMeterRegistry, got " + meterRegistry.getClass().getName());
    }

    @Test
    @DisplayName("Prometheus scrape output contains the custom meters")
    void prometheusEndpointExposesCustomMetrics() {
        // Actuator runs on its own port, so this goes to the management context rather than
        // through the authenticated application chain — exactly as a scraper would.
        var response = managementRestTemplate()
                .getForEntity("/actuator/prometheus", String.class);

        String scrape = response.getBody();
        assertTrue(scrape != null && !scrape.isBlank(),
                "scrape should not be empty; status was " + response.getStatusCode());

        // Micrometer converts dots to underscores for Prometheus. Asserting on the exported
        // names rather than the Java-side ones is the point: the two can differ, and did —
        // a counter named splitwise.expenses.created was being rewritten to
        // splitwise_expenses_total because OpenMetrics reserves the _created suffix.
        assertTrue(scrape.contains("splitwise_outbox_pending"), "missing outbox pending gauge");
        assertTrue(scrape.contains("splitwise_expenses_accepted"), "missing expense counter");
        assertTrue(scrape.contains("splitwise_payments_recorded"), "missing payment counter");
        assertTrue(scrape.contains("splitwise_convergence_lag"), "missing convergence timer");
        assertTrue(scrape.contains("jvm_memory_used_bytes"), "missing auto-bound JVM metrics");
        assertTrue(scrape.contains("kafka_consumer"), "missing auto-bound Kafka client metrics");
    }
}
