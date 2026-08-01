package com.project.Splitwise.service;

import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.domain.settlement.UserBalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Nets per-user balances into a set of transfers that clears every debt in a group.
 *
 * <h2>What this is, and what it is not</h2>
 *
 * This is a greedy largest-creditor / largest-debtor heuristic. It is <em>not</em> an
 * exact minimiser of transfer count. Minimising transfers is NP-hard: any subset of
 * users whose balances sum to zero can be settled among themselves, and locating those
 * subsets is subset-sum. The greedy pass does guarantee an upper bound of {@code n-1}
 * transfers for {@code n} participants with non-zero balances, because every iteration
 * drives at least one participant to exactly zero and removes them from the queues.
 *
 * <p>Concretely, greedy is beatable. For balances {@code [+5, +5, -5, -5]} greedy and
 * optimal both find 2 transfers, but for {@code [+3, +2, -5, +5, -5]} greedy can emit
 * one more transfer than the subset-aware optimum. That trade is deliberate: greedy is
 * O(n log n) and always correct (all debts clear), whereas exact minimisation is
 * exponential and not worth it for realistic group sizes.
 */
public final class SettlementEngine {

    /** Currency sub-unit precision. All transfer amounts are emitted at this scale. */
    private static final int MONETARY_SCALE = 2;

    private SettlementEngine() {
    }

    public static List<Settlement> settle(List<UserBalance> balances) {
        if (balances == null || balances.isEmpty()) {
            return List.of();
        }

        // Ties broken by userId so the output is deterministic and therefore assertable
        // in tests. Without the tie-break, PriorityQueue ordering among equal balances
        // is unspecified and the test suite becomes flaky.
        PriorityQueue<Position> creditors = new PriorityQueue<>(
                Comparator.comparing(Position::amount, Comparator.<BigDecimal>reverseOrder())
                        .thenComparing(Position::userId));

        PriorityQueue<Position> debtors = new PriorityQueue<>(
                Comparator.comparing(Position::amount, Comparator.<BigDecimal>naturalOrder())
                        .thenComparing(Position::userId));

        for (UserBalance balance : balances) {
            BigDecimal net = normalise(balance.netBalance());
            int sign = net.signum();
            if (sign > 0) {
                creditors.add(new Position(balance.userId(), net));
            } else if (sign < 0) {
                debtors.add(new Position(balance.userId(), net));
            }
        }

        List<Settlement> transfers = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Position creditor = creditors.poll();
            Position debtor = debtors.poll();

            BigDecimal transfer = creditor.amount().min(debtor.amount().negate());

            transfers.add(new Settlement(debtor.userId(), creditor.userId(), transfer));

            BigDecimal creditorRemainder = creditor.amount().subtract(transfer);
            BigDecimal debtorRemainder = debtor.amount().add(transfer);

            // Whichever side did not hit zero goes back in the queue.
            if (creditorRemainder.signum() > 0) {
                creditors.add(new Position(creditor.userId(), creditorRemainder));
            }
            if (debtorRemainder.signum() < 0) {
                debtors.add(new Position(debtor.userId(), debtorRemainder));
            }
        }

        return transfers;
    }

    private static BigDecimal normalise(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY)
                : value.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /** Mutable-by-replacement working copy, so caller input is never touched. */
    private record Position(Long userId, BigDecimal amount) {
    }
}
