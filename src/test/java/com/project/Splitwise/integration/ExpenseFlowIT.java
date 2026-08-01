package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import com.project.Splitwise.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: HTTP write -> outbox -> Kafka -> balance write model -> read projections.
 *
 * <p>Every step here is asynchronous after the HTTP response returns, so the assertions
 * poll for convergence rather than sleeping a fixed amount and hoping.
 */
class ExpenseFlowIT extends AbstractIntegrationTest {

    /** Fresh group per test so the shared containers do not leak state between cases. */
    private static final AtomicLong GROUP_IDS = new AtomicLong(9_000);

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GroupBalanceViewRepository balanceViews;
    @Autowired
    private SettlementViewRepository settlementViews;
    @Autowired
    private OutboxEventRepository outboxEvents;

    private static CreateExpenseRequest equalSplit(long groupId, String amount, List<Long> participants) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(participants.get(0));
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(participants);
        return req;
    }

    @Test
    @DisplayName("an expense posted over HTTP converges into the balance projection")
    void expensePropagatesToReadModel() {
        long groupId = GROUP_IDS.incrementAndGet();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/expenses", equalSplit(groupId, "300.00", List.of(1L, 2L, 3L)), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var views = balanceViews.findByGroupId(groupId);
            assertEquals(3, views.size());

            // Payer fronted 300 and owes 100 of it, so they are up 200.
            var payer = views.stream().filter(v -> v.getUserId() == 1L).findFirst().orElseThrow();
            assertEquals(0, payer.getNetBalance().compareTo(new BigDecimal("200.00")));

            // And the group nets to zero, which is the invariant that must never break.
            BigDecimal total = views.stream()
                    .map(v -> v.getNetBalance())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, total.compareTo(BigDecimal.ZERO));
        });
    }

    @Test
    @DisplayName("settlement_view is actually populated — it used to be permanently empty")
    void settlementProjectionIsPopulated() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "300.00", List.of(1L, 2L, 3L)), String.class);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<SettlementView> plan = settlementViews.findByGroupId(groupId);
            assertEquals(2, plan.size());

            BigDecimal owed = plan.stream()
                    .map(SettlementView::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, owed.compareTo(new BigDecimal("200.00")));

            assertTrue(plan.stream().allMatch(s -> s.getToUser() == 1L),
                    "everyone should be paying the person who fronted the bill");
        });
    }

    @Test
    @DisplayName("the outbox drains: no event is left staged once the relay has run")
    void outboxDrainsToEmpty() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "99.99", List.of(1L, 2L, 3L)), String.class);

        await().atMost(Duration.ofSeconds(30))
                .until(() -> outboxEvents.countByPublishedAtIsNull() == 0);
    }

    @Test
    @DisplayName("a 10.00 three-way split still nets to zero across the projection")
    void indivisibleSplitStillBalances() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "10.00", List.of(1L, 2L, 3L)), String.class);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var views = balanceViews.findByGroupId(groupId);
            assertEquals(3, views.size());

            BigDecimal total = views.stream()
                    .map(v -> v.getNetBalance())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // If shares were rounded independently this lands on +/- 0.01 instead of 0.
            assertEquals(0, total.compareTo(BigDecimal.ZERO),
                    () -> "group did not net to zero: " + views);
        });
    }

    @Test
    void rejectsExactSharesThatDoNotSumToTotal() {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(GROUP_IDS.incrementAndGet());
        req.setPaidBy(1L);
        req.setAmount(new BigDecimal("100.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EXACT);

        CreateExpenseRequest.Share share = new CreateExpenseRequest.Share();
        share.setUserId(1L);
        share.setAmount(new BigDecimal("99.99"));
        req.setShares(List.of(share));

        ResponseEntity<String> response = restTemplate.postForEntity("/expenses", req, String.class);

        assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError(),
                "a non-balancing split must not be accepted, got " + response.getStatusCode());
    }
}
