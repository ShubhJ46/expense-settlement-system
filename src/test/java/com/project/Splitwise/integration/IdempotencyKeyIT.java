package com.project.Splitwise.integration;

import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.dto.RecordPaymentRequest;
import com.project.Splitwise.readmodel.repository.GroupBalanceViewRepository;
import com.project.Splitwise.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client-retry hole, from the outside.
 *
 * <p>Everything else in this service protects the path from the database onwards. These tests
 * cover the hop in front of it: a client that never learns whether its request succeeded and
 * sends it again. Without an idempotency key that produces two entirely valid expenses, each
 * applied exactly once — internally consistent and still wrong.
 */
class IdempotencyKeyIT extends AbstractIntegrationTest {

    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private GroupBalanceViewRepository balanceViews;

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

    private <T> HttpEntity<T> withKey(TestUser user, T body, String key) {
        HttpHeaders headers = authHeaders(user);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> postExpense(TestUser user, CreateExpenseRequest req, String key) {
        return restTemplate.exchange("/expenses", HttpMethod.POST, withKey(user, req, key), String.class);
    }

    private long expenseCount() {
        return expenses.findByGroupId(groupId, PageRequest.of(0, 100)).getTotalElements();
    }

    @Test
    @DisplayName("replaying a request with the same key creates one expense, not two")
    void retryWithSameKeyIsNotDuplicated() {
        String key = UUID.randomUUID().toString();

        ResponseEntity<String> first = postExpense(payer, expense("100.00"), key);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());

        // Exactly what a client does when the first response never arrives.
        ResponseEntity<String> retry = postExpense(payer, expense("100.00"), key);
        assertEquals(HttpStatus.CREATED, retry.getStatusCode());

        assertEquals(first.getBody(), retry.getBody(),
                "a replay should return the original expense, not a new one");
        assertEquals(1, expenseCount(), "the retry must not have created a second expense");
    }

    @Test
    @DisplayName("the replay does not emit a second event, so balances are charged once")
    void replayDoesNotDoubleChargeBalances() {
        String key = UUID.randomUUID().toString();

        postExpense(payer, expense("100.00"), key);
        postExpense(payer, expense("100.00"), key);
        postExpense(payer, expense("100.00"), key);

        // The payer fronted 100 of a 100 bill split two ways, so they are owed 50 — and stay
        // owed 50 no matter how many times the request is replayed. Held for a few seconds so
        // a late duplicate event would have time to land and be caught.
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            BigDecimal net = balanceViews.findByGroupId(groupId).stream()
                    .filter(v -> v.getUserId().equals(payer.id()))
                    .map(v -> v.getNetBalance())
                    .findFirst()
                    .orElse(null);
            assertNotNull(net, "no projected balance yet");
            assertEquals(0, new BigDecimal("50.00").compareTo(net), "expected 50.00 but was " + net);
        });
    }

    @Test
    @DisplayName("without a key, a retry does create a second expense")
    void withoutKeyRetryDuplicates() {
        postExpense(payer, expense("100.00"), null);
        postExpense(payer, expense("100.00"), null);

        // Not a bug being asserted, but the reason the header exists: identical requests are
        // legitimately two different expenses unless the client says otherwise.
        assertEquals(2, expenseCount());
    }

    @Test
    @DisplayName("reusing a key with a different body is refused")
    void sameKeyDifferentBodyIsRejected() {
        String key = UUID.randomUUID().toString();

        assertEquals(HttpStatus.CREATED, postExpense(payer, expense("100.00"), key).getStatusCode());

        ResponseEntity<String> conflicting = postExpense(payer, expense("250.00"), key);

        assertEquals(HttpStatus.CONFLICT, conflicting.getStatusCode());
        assertEquals(1, expenseCount(), "the conflicting request must not have been written");
    }

    @Test
    @DisplayName("one user's key cannot collide with another user's")
    void keysAreScopedPerUser() {
        String key = "shared-key-" + UUID.randomUUID();

        assertEquals(HttpStatus.CREATED, postExpense(payer, expense("100.00"), key).getStatusCode());

        // Same key, different caller, different body. Scoped per user, so this is simply a
        // new request rather than a conflict or a leak of the first caller's response.
        CreateExpenseRequest theirs = expense("40.00");
        theirs.setPaidBy(other.id());
        assertEquals(HttpStatus.CREATED, postExpense(other, theirs, key).getStatusCode());

        assertEquals(2, expenseCount());
    }

    @Test
    @DisplayName("a replayed payment discharges the debt once, not twice")
    void replayedPaymentIsNotAppliedTwice() {
        postExpense(payer, expense("100.00"), null);

        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            BigDecimal net = balanceViews.findByGroupId(groupId).stream()
                    .filter(v -> v.getUserId().equals(other.id()))
                    .map(v -> v.getNetBalance()).findFirst().orElse(null);
            assertNotNull(net);
            assertEquals(0, new BigDecimal("-50.00").compareTo(net));
        });

        RecordPaymentRequest payment = new RecordPaymentRequest();
        payment.setFromUserId(other.id());
        payment.setToUserId(payer.id());
        payment.setAmount(new BigDecimal("50.00"));

        String key = UUID.randomUUID().toString();
        String path = "/groups/" + groupId + "/settlements";

        ResponseEntity<String> first = restTemplate.exchange(
                path, HttpMethod.POST, withKey(other, payment, key), String.class);
        ResponseEntity<String> retry = restTemplate.exchange(
                path, HttpMethod.POST, withKey(other, payment, key), String.class);

        assertEquals(HttpStatus.ACCEPTED, first.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, retry.getStatusCode());
        assertEquals(first.getBody(), retry.getBody());

        // Applied twice, the debtor would land on +50 rather than 0 and the group would be
        // short the money that was never actually paid.
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            BigDecimal net = balanceViews.findByGroupId(groupId).stream()
                    .filter(v -> v.getUserId().equals(other.id()))
                    .map(v -> v.getNetBalance()).findFirst().orElse(null);
            assertNotNull(net);
            assertEquals(0, BigDecimal.ZERO.compareTo(net), "expected 0.00 but was " + net);
        });

        assertTrue(restTemplate.exchange("/groups/" + groupId + "/payments", HttpMethod.GET,
                        as(other), String.class).getBody().split("\"id\"").length - 1 == 1,
                "exactly one payment should have been recorded");
    }
}
