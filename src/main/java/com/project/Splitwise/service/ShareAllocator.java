package com.project.Splitwise.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits a monetary amount across participants so the parts sum to <em>exactly</em> the
 * original total.
 *
 * <h2>Why this is not just {@code total / n}</h2>
 *
 * 10.00 split three ways is 3.3333...; rounding each part to 3.33 loses a cent and
 * rounding each up to 3.34 invents one. Either way the group's balances stop summing to
 * zero, and because balances are cumulative that error compounds with every expense until
 * settlements no longer clear.
 *
 * <p>The fix is the largest-remainder method: work in integer minor units (paise/cents),
 * give everyone the floor, then hand the leftover units out one at a time. Nobody is ever
 * off by more than one minor unit, and the sum is exact by construction rather than by
 * rounding luck.
 */
public final class ShareAllocator {

    private static final int MONETARY_SCALE = 2;
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = BigDecimal.valueOf(100);

    private ShareAllocator() {
    }

    /**
     * Equal split. Leftover minor units go to the lowest-indexed participants, so the
     * result is deterministic for a given participant ordering.
     */
    public static List<BigDecimal> allocateEqually(BigDecimal total, int participants) {
        if (participants <= 0) {
            throw new IllegalArgumentException("participants must be positive, was " + participants);
        }

        long totalMinor = toMinorUnits(total);
        long base = Math.floorDiv(totalMinor, participants);
        long leftover = Math.floorMod(totalMinor, participants);

        List<BigDecimal> allocation = new ArrayList<>(participants);
        for (int i = 0; i < participants; i++) {
            allocation.add(fromMinorUnits(base + (i < leftover ? 1 : 0)));
        }
        return allocation;
    }

    /**
     * Weighted split, same guarantee. Used for percentage or ratio splits.
     *
     * <p>Each participant gets the floor of their exact entitlement; the units left over
     * are handed to whoever was rounded down hardest (largest fractional remainder first,
     * ties broken by index).
     */
    public static List<BigDecimal> allocateByWeight(BigDecimal total, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        if (weights.stream().anyMatch(w -> w == null || w.signum() < 0)) {
            throw new IllegalArgumentException("weights must be non-null and non-negative");
        }

        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() == 0) {
            throw new IllegalArgumentException("weights must not sum to zero");
        }

        long totalMinor = toMinorUnits(total);
        BigDecimal totalMinorDecimal = BigDecimal.valueOf(totalMinor);

        long[] floors = new long[weights.size()];
        List<Remainder> remainders = new ArrayList<>(weights.size());
        long assigned = 0;

        for (int i = 0; i < weights.size(); i++) {
            // exact entitlement, kept at high precision so the remainder is meaningful
            BigDecimal exact = totalMinorDecimal
                    .multiply(weights.get(i))
                    .divide(weightSum, 10, RoundingMode.HALF_UP);

            long floor = exact.setScale(0, RoundingMode.FLOOR).longValueExact();
            floors[i] = floor;
            assigned += floor;
            remainders.add(new Remainder(i, exact.subtract(BigDecimal.valueOf(floor))));
        }

        long leftover = totalMinor - assigned;

        remainders.sort(Comparator
                .comparing(Remainder::fraction, Comparator.<BigDecimal>reverseOrder())
                .thenComparingInt(Remainder::index));

        for (int i = 0; i < leftover; i++) {
            floors[remainders.get(i).index()]++;
        }

        List<BigDecimal> allocation = new ArrayList<>(weights.size());
        for (long minor : floors) {
            allocation.add(fromMinorUnits(minor));
        }
        return allocation;
    }

    private static long toMinorUnits(BigDecimal total) {
        if (total == null) {
            throw new IllegalArgumentException("total must not be null");
        }

        // Reject sub-paisa input rather than rounding it. Rounding here would be worse than
        // it looks: a caller sending 10.005 would get back shares summing to 10.01, i.e.
        // the system would quietly invent a paisa and hand the group a total they never
        // agreed to. Callers round before splitting, or they get an error.
        if (total.stripTrailingZeros().scale() > MONETARY_SCALE) {
            throw new ArithmeticException(
                    "amount " + total.toPlainString() + " carries more than " + MONETARY_SCALE
                            + " decimal places and cannot be split exactly");
        }

        return total.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY)
                .movePointRight(MONETARY_SCALE)
                .longValueExact();
    }

    private static BigDecimal fromMinorUnits(long minor) {
        return BigDecimal.valueOf(minor)
                .divide(MINOR_UNITS_PER_MAJOR, MONETARY_SCALE, RoundingMode.UNNECESSARY);
    }

    private record Remainder(int index, BigDecimal fraction) {
    }
}
