package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settle-up loop: owe, pay, and stop owing.
 *
 * <p>Before this existed the engine could tell you who should pay whom, but nothing could
 * record that they had, so balances only ever grew.
 */
class SettleUpIT extends AbstractIntegrationTest {

    @Autowired
    private GroupBalanceViewRepository balanceViews;

    private TestUser payer;
    private TestUser debtor;
    private TestUser other;
    private Long groupId;

    @BeforeEach
    void setUpGroup() {
        payer = registerUser();
        debtor = registerUser();
        other = registerUser();
        groupId = createGroup(payer, debtor, other);
    }

    private void postEqualExpense(String amount) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer.id());
        req.setAmount(new BigDecimal(amount));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(List.of(payer.id(), debtor.id(), other.id()));

        restTemplate.exchange("/expenses", HttpMethod.POST, as(payer, req), String.class);
    }

    private ResponseEntity<String> pay(TestUser caller, TestUser from, TestUser to, String amount) {
        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setFromUserId(from.id());
        req.setToUserId(to.id());
        req.setAmount(new BigDecimal(amount));

        return restTemplate.exchange("/groups/" + groupId + "/settlements",
                HttpMethod.POST, as(caller, req), String.class);
    }

    /** Polls the read model until the given user's net balance matches. */
    private void awaitNet(TestUser user, String expected) {
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            BigDecimal actual = balanceViews.findByGroupId(groupId).stream()
                    .filter(v -> v.getUserId().equals(user.id()))
                    .map(v -> v.getNetBalance())
                    .findFirst()
                    .orElse(null);
            assertNotNull(actual, () -> "no projected balance yet for user " + user.id());
            assertEquals(0, new BigDecimal(expected).compareTo(actual),
                    () -> "expected " + expected + " but was " + actual);
        });
    }

    @Test
    @DisplayName("paying a debt in full clears it from the balance projection")
    void paymentClearsTheDebt() {
        postEqualExpense("300.00");
        awaitNet(debtor, "-100.00");

        assertEquals(HttpStatus.ACCEPTED, pay(debtor, debtor, payer, "100.00").getStatusCode());

        // The debt is discharged asynchronously, through the same outbox and consumer path
        // as the expense that created it.
        awaitNet(debtor, "0.00");
        awaitNet(payer, "100.00");
        awaitNet(other, "-100.00");

        BigDecimal total = balanceViews.findByGroupId(groupId).stream()
                .map(v -> v.getNetBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(BigDecimal.ZERO),
                "the group must still net to zero after a payment");
    }

    @Test
    @DisplayName("once everyone has paid, the settlement engine proposes nothing")
    void fullySettledGroupHasNoSuggestions() {
        postEqualExpense("300.00");
        awaitNet(payer, "200.00");

        ResponseEntity<SettlementResponse> plan = restTemplate.exchange(
                "/groups/" + groupId + "/settlements", HttpMethod.GET, as(payer), SettlementResponse.class);
        assertEquals(2, plan.getBody().getSettlements().size());

        pay(debtor, debtor, payer, "100.00");
        pay(other, other, payer, "100.00");

        awaitNet(payer, "0.00");
        awaitNet(debtor, "0.00");
        awaitNet(other, "0.00");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ResponseEntity<SettlementResponse> settled = restTemplate.exchange(
                    "/groups/" + groupId + "/settlements", HttpMethod.GET, as(payer),
                    SettlementResponse.class);
            assertTrue(settled.getBody().getSettlements().isEmpty(),
                    "a settled group has nothing left to suggest");
        });
    }

    @Test
    @DisplayName("a partial payment reduces the debt without clearing it")
    void partialPaymentReducesTheDebt() {
        postEqualExpense("300.00");
        awaitNet(debtor, "-100.00");

        pay(debtor, debtor, payer, "40.00");

        awaitNet(debtor, "-60.00");
        awaitNet(payer, "160.00");
    }

    @Test
    @DisplayName("a self-transfer is rejected at the API rather than corrupting balances")
    void selfTransferIsRejected() {
        ResponseEntity<String> response = pay(payer, payer, payer, "10.00");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(balanceViews.findByGroupId(groupId).isEmpty(),
                "a rejected payment must not create balance rows");
    }

    @Test
    @DisplayName("a member cannot record a payment between two other people")
    void thirdPartyCannotRecordSomeoneElsesPayment() {
        postEqualExpense("300.00");
        awaitNet(debtor, "-100.00");

        // `other` is a legitimate group member, but this transfer is nothing to do with them.
        ResponseEntity<String> response = pay(other, debtor, payer, "100.00");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        awaitNet(debtor, "-100.00");
    }

    @Test
    @DisplayName("the payee may also record the payment, not just the payer")
    void payeeCanRecordThePayment() {
        postEqualExpense("300.00");
        awaitNet(debtor, "-100.00");

        assertEquals(HttpStatus.ACCEPTED, pay(payer, debtor, payer, "100.00").getStatusCode());
        awaitNet(debtor, "0.00");
    }
}
