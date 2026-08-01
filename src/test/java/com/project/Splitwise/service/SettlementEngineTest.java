package com.project.Splitwise.service;

import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.domain.settlement.UserBalance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementEngineTest {

    private static UserBalance user(long id, String amount) {
        return new UserBalance(id, new BigDecimal(amount));
    }

    /** Applies the transfers and returns the resulting net position of every user. */
    private static Map<Long, BigDecimal> applyTransfers(List<UserBalance> start, List<Settlement> transfers) {
        Map<Long, BigDecimal> net = new HashMap<>();
        for (UserBalance b : start) {
            net.merge(b.userId(), b.netBalance(), BigDecimal::add);
        }
        for (Settlement t : transfers) {
            net.merge(t.getFromUserId(), t.getAmount(), BigDecimal::add);
            net.merge(t.getToUserId(), t.getAmount().negate(), BigDecimal::add);
        }
        return net;
    }

    @Test
    void settlesSimpleThreeWayDebt() {
        List<UserBalance> balances = List.of(user(1, "500.00"), user(2, "-300.00"), user(3, "-200.00"));

        List<Settlement> transfers = SettlementEngine.settle(balances);

        assertEquals(2, transfers.size());
        assertTrue(applyTransfers(balances, transfers).values().stream()
                .allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    @DisplayName("every debt clears: after applying the plan nobody owes anybody")
    void everyBalanceEndsAtZero() {
        List<UserBalance> balances = List.of(
                user(1, "120.50"), user(2, "-45.25"), user(3, "-75.25"),
                user(4, "10.00"), user(5, "-10.00"));

        Map<Long, BigDecimal> after = applyTransfers(balances, SettlementEngine.settle(balances));

        after.forEach((userId, net) ->
                assertEquals(0, net.compareTo(BigDecimal.ZERO), () -> "user " + userId + " left at " + net));
    }

    @Test
    @DisplayName("transfer count stays within the n-1 bound the greedy pass guarantees")
    void respectsUpperBoundOnTransferCount() {
        Random random = new Random(20260801L);

        for (int trial = 0; trial < 200; trial++) {
            int users = 2 + random.nextInt(9);
            List<UserBalance> balances = new ArrayList<>();
            BigDecimal running = BigDecimal.ZERO;

            for (int i = 1; i < users; i++) {
                BigDecimal amount = BigDecimal.valueOf(random.nextInt(40001) - 20000, 2);
                balances.add(new UserBalance((long) i, amount));
                running = running.add(amount);
            }
            // Balances in a group always sum to zero; the last user absorbs the remainder.
            balances.add(new UserBalance((long) users, running.negate()));

            List<Settlement> transfers = SettlementEngine.settle(balances);

            long nonZero = balances.stream().filter(b -> b.netBalance().signum() != 0).count();
            assertTrue(transfers.size() <= Math.max(0, nonZero - 1),
                    () -> "got " + transfers.size() + " transfers for " + nonZero + " non-zero participants");

            applyTransfers(balances, transfers).forEach((userId, net) ->
                    assertEquals(0, net.compareTo(BigDecimal.ZERO), () -> "user " + userId + " left at " + net));
        }
    }

    @Test
    void noTransferIsZeroOrNegative() {
        List<UserBalance> balances = List.of(
                user(1, "60.00"), user(2, "-20.00"), user(3, "-20.00"), user(4, "-20.00"));

        for (Settlement transfer : SettlementEngine.settle(balances)) {
            assertTrue(transfer.getAmount().signum() > 0, () -> "non-positive transfer: " + transfer);
            assertNotEquals(transfer.getFromUserId(), transfer.getToUserId(), "self-transfer emitted");
        }
    }

    @Test
    @DisplayName("output is deterministic so the projection is stable across replays")
    void producesDeterministicOutput() {
        List<UserBalance> balances = List.of(
                user(3, "50.00"), user(1, "50.00"), user(2, "-50.00"), user(4, "-50.00"));

        List<Settlement> first = SettlementEngine.settle(balances);
        for (int i = 0; i < 25; i++) {
            assertEquals(first, SettlementEngine.settle(balances));
        }
    }

    @Test
    void doesNotMutateItsInput() {
        UserBalance creditor = user(1, "100.00");
        List<UserBalance> balances = List.of(creditor, user(2, "-100.00"));

        SettlementEngine.settle(balances);

        assertEquals(new BigDecimal("100.00"), creditor.netBalance());
    }

    @Test
    void handlesEmptyAndSingleUserGroups() {
        assertTrue(SettlementEngine.settle(List.of()).isEmpty());
        assertTrue(SettlementEngine.settle(null).isEmpty());
        // A lone non-zero balance has no counterparty, so there is nothing to settle.
        assertTrue(SettlementEngine.settle(List.of(user(1, "100.00"))).isEmpty());
    }

    @Test
    void ignoresUsersWhoAreAlreadySquare() {
        List<Settlement> transfers = SettlementEngine.settle(List.of(
                user(1, "25.00"), user(2, "0.00"), user(3, "-25.00")));

        assertEquals(1, transfers.size());
        assertEquals(3L, transfers.get(0).getFromUserId());
        assertEquals(1L, transfers.get(0).getToUserId());
    }
}
