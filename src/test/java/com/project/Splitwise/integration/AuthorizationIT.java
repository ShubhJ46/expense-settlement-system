package com.project.Splitwise.integration;

import com.project.Splitwise.dto.AuthDtos;
import com.project.Splitwise.dto.CreateExpenseRequest;
import com.project.Splitwise.dto.GroupDtos;
import com.project.Splitwise.dto.RecordPaymentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authorization boundary.
 *
 * <p>Every endpoint here was previously {@code permitAll} with no notion of a caller, so an
 * unauthenticated request could post an expense to any group naming any user as payer. These
 * tests are the regression net for that.
 */
class AuthorizationIT extends AbstractIntegrationTest {

    private CreateExpenseRequest expenseIn(Long groupId, Long payer, List<Long> participants) {
        CreateExpenseRequest req = new CreateExpenseRequest();
        req.setGroupId(groupId);
        req.setPaidBy(payer);
        req.setAmount(new BigDecimal("300.00"));
        req.setSplitType(CreateExpenseRequest.SplitType.EQUAL);
        req.setParticipants(participants);
        return req;
    }

    @Test
    @DisplayName("every business endpoint refuses an unauthenticated caller")
    void anonymousAccessIsRefused() {
        record Call(HttpMethod method, String path) {
        }

        List<Call> calls = List.of(
                new Call(HttpMethod.GET, "/expenses?groupId=1"),
                new Call(HttpMethod.POST, "/expenses"),
                new Call(HttpMethod.GET, "/balances/1"),
                new Call(HttpMethod.GET, "/settlements/1"),
                new Call(HttpMethod.GET, "/groups/1/settlements"),
                new Call(HttpMethod.POST, "/groups/1/settlements"),
                new Call(HttpMethod.GET, "/groups/1/payments"),
                new Call(HttpMethod.GET, "/groups"),
                new Call(HttpMethod.POST, "/groups"));

        for (Call call : calls) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    call.path(), call.method(), new HttpEntity<>("{}", headers), String.class);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    () -> call.method() + " " + call.path() + " should require authentication");
        }
    }

    @Test
    @DisplayName("a garbage or forged bearer token is not accepted")
    void invalidTokenIsRefused() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not.a.jwt");

        ResponseEntity<String> response = restTemplate.exchange(
                "/groups", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("a non-member cannot read another group's balances, plan or payments")
    void outsiderCannotReadGroupData() {
        TestUser member = registerUser();
        TestUser outsider = registerUser();
        Long groupId = createGroup(member);

        List<String> readPaths = List.of(
                "/balances/" + groupId,
                "/settlements/" + groupId,
                "/groups/" + groupId + "/settlements",
                "/groups/" + groupId + "/payments",
                "/expenses?groupId=" + groupId);

        for (String path : readPaths) {
            ResponseEntity<String> response = restTemplate.exchange(
                    path, HttpMethod.GET, as(outsider), String.class);

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    () -> path + " should be forbidden to a non-member");
        }
    }

    @Test
    @DisplayName("a non-member cannot write an expense into someone else's group")
    void outsiderCannotWriteExpense() {
        TestUser member = registerUser();
        TestUser outsider = registerUser();
        Long groupId = createGroup(member);

        ResponseEntity<String> response = restTemplate.exchange("/expenses", HttpMethod.POST,
                as(outsider, expenseIn(groupId, outsider.id(), List.of(outsider.id(), member.id()))),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("a member cannot charge a share to somebody outside the group")
    void cannotChargeANonMember() {
        TestUser member = registerUser();
        TestUser stranger = registerUser();
        Long groupId = createGroup(member);

        ResponseEntity<String> response = restTemplate.exchange("/expenses", HttpMethod.POST,
                as(member, expenseIn(groupId, member.id(), List.of(member.id(), stranger.id()))),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "charging a share to a non-member must be refused");
    }

    @Test
    @DisplayName("a non-member cannot record a payment in someone else's group")
    void outsiderCannotRecordPayment() {
        TestUser member = registerUser();
        TestUser outsider = registerUser();
        Long groupId = createGroup(member);

        RecordPaymentRequest req = new RecordPaymentRequest();
        req.setFromUserId(outsider.id());
        req.setToUserId(member.id());
        req.setAmount(new BigDecimal("10.00"));

        ResponseEntity<String> response = restTemplate.exchange(
                "/groups/" + groupId + "/settlements", HttpMethod.POST,
                as(outsider, req), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("GET /groups lists only the caller's own groups")
    void groupListingIsScopedToTheCaller() {
        TestUser mine = registerUser();
        TestUser theirs = registerUser();

        Long myGroup = createGroup(mine);
        Long theirGroup = createGroup(theirs);

        ResponseEntity<String> response = restTemplate.exchange(
                "/groups", HttpMethod.GET, as(mine), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"id\":" + myGroup));
        assertFalse(response.getBody().contains("\"id\":" + theirGroup),
                "another user's group must not appear in this listing");
    }

    @Test
    @DisplayName("registering twice with the same email is refused")
    void duplicateEmailIsRejected() {
        String email = "dupe-" + UUID.randomUUID() + "@example.test";
        var request = new AuthDtos.RegisterRequest(email, "correct-horse-battery", "First");

        assertEquals(HttpStatus.CREATED,
                restTemplate.postForEntity("/auth/register", request, String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,
                restTemplate.postForEntity("/auth/register", request, String.class).getStatusCode());
    }

    @Test
    @DisplayName("login returns a usable token; a wrong password does not")
    void loginIssuesAWorkingToken() {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        restTemplate.postForEntity("/auth/register",
                new AuthDtos.RegisterRequest(email, "correct-horse-battery", "Login Test"),
                String.class);

        ResponseEntity<AuthDtos.AuthResponse> ok = restTemplate.postForEntity("/auth/login",
                new AuthDtos.LoginRequest(email, "correct-horse-battery"), AuthDtos.AuthResponse.class);

        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertNotNull(ok.getBody().token());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ok.getBody().token());
        assertEquals(HttpStatus.OK, restTemplate.exchange(
                "/groups", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode());

        ResponseEntity<String> wrong = restTemplate.postForEntity("/auth/login",
                new AuthDtos.LoginRequest(email, "wrong-password"), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, wrong.getStatusCode());
    }

    @Test
    @DisplayName("the password hash never appears in a response")
    void passwordHashIsNeverReturned() {
        String email = "hash-" + UUID.randomUUID() + "@example.test";

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register",
                new AuthDtos.RegisterRequest(email, "correct-horse-battery", "Hash Test"),
                String.class);

        assertFalse(response.getBody().toLowerCase().contains("password"),
                "registration response leaked a password field: " + response.getBody());
        assertFalse(response.getBody().contains("$2a$"), "response contained a bcrypt hash");
    }

    @Test
    @DisplayName("a member can add another user, who then gains access")
    void addedMemberGainsAccess() {
        TestUser owner = registerUser();
        TestUser joiner = registerUser();
        Long groupId = createGroup(owner);

        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange(
                "/balances/" + groupId, HttpMethod.GET, as(joiner), String.class).getStatusCode());

        restTemplate.exchange("/groups/" + groupId + "/members", HttpMethod.POST,
                as(owner, new GroupDtos.AddMemberRequest(joiner.id())), String.class);

        assertEquals(HttpStatus.OK, restTemplate.exchange(
                "/balances/" + groupId, HttpMethod.GET, as(joiner), String.class).getStatusCode());
    }
}
