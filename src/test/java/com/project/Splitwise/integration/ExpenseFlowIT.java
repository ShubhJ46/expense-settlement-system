package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.readmodel.SettlementView;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import com.project.Splitwise.readmodel.repository.SettlementViewRepository;
import com.project.Splitwise.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

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

    @Autowired
    private GroupBalanceViewRepository balanceViews;
    @Autowired
    private SettlementViewRepository settlementViews;
    @Autowired
    private OutboxEventRepository outboxEvents;

    private TestUser payer;
    private TestUser second;
    private TestUser third;
    private Long groupId;

    /** A real group with real members, since the API now refuses anything else. */
    @BeforeEach
    void setUpGroup() {
        payer = registerUser();
        second = registerUser();
        third = registerUser();
        groupId = createGroup(payer, second, third);
    }

    private List<Long> everyone() {
        return List.of(payer.id(), second.id(), third.id());
    }

    private CreateExpenseRequest equalSplit(String amount) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(everyone());
        return req;
    }

    private ResponseEntity<String> postExpense(CreateExpenseRequest req) {
        return restTemplate.exchange("/expenses", HttpMethod.POST, as(payer, req), String.class);
    }

    @Test
    @DisplayName("an expense posted over HTTP converges into the balance projection")
    void expensePropagatesToReadModel() {
        ResponseEntity<String> response = postExpense(equalSplit("300.00"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var views = balanceViews.findByGroupId(groupId);
            assertEquals(3, views.size());

            // Payer fronted 300 and owes 100 of it, so they are up 200.
            var view = views.stream()
                    .filter(v -> v.getUserId().equals(payer.id()))
                    .findFirst().orElseThrow();
            assertEquals(0, view.getNetBalance().compareTo(new BigDecimal("200.00")));

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
        postExpense(equalSplit("300.00"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<SettlementView> plan = settlementViews.findByGroupId(groupId);
            assertEquals(2, plan.size());

            BigDecimal owed = plan.stream()
                    .map(SettlementView::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, owed.compareTo(new BigDecimal("200.00")));

            assertTrue(plan.stream().allMatch(s -> s.getToUser().equals(payer.id())),
                    "everyone should be paying the person who fronted the bill");
        });
    }

    @Test
    @DisplayName("the outbox drains: no event is left staged once the relay has run")
    void outboxDrainsToEmpty() {
        postExpense(equalSplit("99.99"));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> outboxEvents.countByPublishedAtIsNull() == 0);
    }

    @Test
    @DisplayName("a 10.00 three-way split still nets to zero across the projection")
    void indivisibleSplitStillBalances() {
        postExpense(equalSplit("10.00"));

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
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal("100.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EXACT);

        CreateExpenseRequest.Share share = new CreateExpenseRequest.Share();
        share.setUserId(payer.id());
        share.setAmount(new BigDecimal("99.99"));
        req.setShares(List.of(share));

        ResponseEntity<String> response = postExpense(req);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "a non-balancing split is the caller's error, not a server fault");
    }

    @Test
    @DisplayName("expenses are listed per group and paged, not dumped wholesale")
    void expenseListingIsScopedAndPaged() {
        postExpense(equalSplit("300.00"));
        postExpense(equalSplit("60.00"));

        ResponseEntity<String> page = restTemplate.exchange(
                "/expenses?groupId=" + groupId + "&size=1", HttpMethod.GET, as(payer), String.class);

        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertTrue(page.getBody().contains("\"totalElements\":2"),
                "expected a page of 2 total, got " + page.getBody());
        assertTrue(page.getBody().contains("\"size\":1"), "page size should be honoured");
    }
}
