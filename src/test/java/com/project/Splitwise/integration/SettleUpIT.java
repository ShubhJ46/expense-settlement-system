package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settle-up loop: owe, pay, and stop owing.
 *
 * <p>Before this existed the engine could tell you who should pay whom, but nothing could
 * record that they had, so balances only ever grew.
 */
class SettleUpIT extends AbstractIntegrationTest {

    private static final AtomicLong GROUP_IDS = new AtomicLong(11_000);

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GroupBalanceViewRepository balanceViews;

    private static CreateExpenseRequest equalSplit(long groupId, String amount, List<Long> participants) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(participants.get(0));
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(participants);
        return req;
    }

    private static RecordPaymentRequest payment(long from, long to, String amount) {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setFromUserId(from);
        req.setToUserId(to);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    /** Polls the read model until the given user's net balance matches. */
    private void awaitNet(long groupId, long userId, String expected) {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            BigDecimal actual = balanceViews.findByGroupId(groupId).stream()
                    .filter(v -> v.getUserId() == userId)
                    .map(v -> v.getNetBalance())
                    .findFirst()
                    .orElse(null);
            assertNotNull(actual, () -> "no projected balance yet for user " + userId);
            assertEquals(0, new BigDecimal(expected).compareTo(actual),
                    () -> "expected " + expected + " but was " + actual);
        });
    }

    private BigDecimal groupTotal(long groupId) {
        return balanceViews.findByGroupId(groupId).stream()
                .map(v -> v.getNetBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("paying a debt in full clears it from the balance projection")
    void paymentClearsTheDebt() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "300.00", List.of(1L, 2L, 3L)), String.class);

        awaitNet(groupId, 2L, "-100.00");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/groups/" + groupId + "/settlements", payment(2L, 1L, "100.00"), String.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        // The debt is discharged asynchronously, through the same outbox and consumer path
        // as the expense that created it.
        awaitNet(groupId, 2L, "0.00");
        awaitNet(groupId, 1L, "100.00");
        awaitNet(groupId, 3L, "-100.00");

        assertEquals(0, groupTotal(groupId).compareTo(BigDecimal.ZERO),
                "the group must still net to zero after a payment");
    }

    @Test
    @DisplayName("once everyone has paid, the settlement engine proposes nothing")
    void fullySettledGroupHasNoSuggestions() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "300.00", List.of(1L, 2L, 3L)), String.class);
        awaitNet(groupId, 1L, "200.00");

        // The plan says 2 and 3 each owe 1 a hundred. Pay exactly that.
        ResponseEntity<SettlementResponse> plan = restTemplate.getForEntity(
                "/groups/" + groupId + "/settlements", SettlementResponse.class);
        assertEquals(2, plan.getBody().getSettlements().size());

        restTemplate.postForEntity("/groups/" + groupId + "/settlements",
                payment(2L, 1L, "100.00"), String.class);
        restTemplate.postForEntity("/groups/" + groupId + "/settlements",
                payment(3L, 1L, "100.00"), String.class);

        awaitNet(groupId, 1L, "0.00");
        awaitNet(groupId, 2L, "0.00");
        awaitNet(groupId, 3L, "0.00");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ResponseEntity<SettlementResponse> settled = restTemplate.getForEntity(
                    "/groups/" + groupId + "/settlements", SettlementResponse.class);
            assertTrue(settled.getBody().getSettlements().isEmpty(),
                    "a settled group has nothing left to suggest");
        });
    }

    @Test
    @DisplayName("a partial payment reduces the debt without clearing it")
    void partialPaymentReducesTheDebt() {
        long groupId = GROUP_IDS.incrementAndGet();

        restTemplate.postForEntity("/expenses",
                equalSplit(groupId, "300.00", List.of(1L, 2L, 3L)), String.class);
        awaitNet(groupId, 2L, "-100.00");

        restTemplate.postForEntity("/groups/" + groupId + "/settlements",
                payment(2L, 1L, "40.00"), String.class);

        awaitNet(groupId, 2L, "-60.00");
        awaitNet(groupId, 1L, "160.00");
    }

    @Test
    @DisplayName("a self-transfer is rejected at the API rather than corrupting balances")
    void selfTransferIsRejected() {
        long groupId = GROUP_IDS.incrementAndGet();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/groups/" + groupId + "/settlements", payment(1L, 1L, "10.00"), String.class);

        assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError(),
                "expected a self-transfer to be refused, got " + response.getStatusCode());

        assertTrue(balanceViews.findByGroupId(groupId).isEmpty(),
                "a rejected payment must not create balance rows");
    }
}
