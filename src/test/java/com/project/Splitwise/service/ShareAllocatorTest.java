package com.project.Splitwise.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareAllocatorTest {

    private static BigDecimal sum(List<BigDecimal> parts) {
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("the canonical failure: 10.00 three ways loses a paisa under naive rounding")
    void splitsIndivisibleAmountWithoutLosingValue() {
        List<BigDecimal> parts = ShareAllocator.allocateEqually(new BigDecimal("10.00"), 3);

        assertEquals(List.of(new BigDecimal("3.34"), new BigDecimal("3.33"), new BigDecimal("3.33")), parts);
        // The invariant that matters: nothing evaporated.
        assertEquals(0, sum(parts).compareTo(new BigDecimal("10.00")));
    }

    @ParameterizedTest(name = "{0} split {1} ways sums back to exactly {0}")
    @CsvSource({
            "10.00, 3", "0.01, 2", "0.02, 3", "100.00, 7", "999.99, 11",
            "1.00, 100", "0.05, 4", "33.33, 3", "1234.56, 13", "5.00, 1"
    })
    void allocationAlwaysSumsToTotal(String total, int participants) {
        BigDecimal amount = new BigDecimal(total);
        List<BigDecimal> parts = ShareAllocator.allocateEqually(amount, participants);

        assertEquals(participants, parts.size());
        assertEquals(0, sum(parts).compareTo(amount),
                () -> "parts " + parts + " did not sum to " + amount);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 7, 13, 97})
    @DisplayName("no participant is ever shortchanged by more than one paisa")
    void spreadIsAtMostOneMinorUnit(int participants) {
        List<BigDecimal> parts = ShareAllocator.allocateEqually(new BigDecimal("100.00"), participants);

        BigDecimal max = parts.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal min = parts.stream().min(BigDecimal::compareTo).orElseThrow();

        assertTrue(max.subtract(min).compareTo(new BigDecimal("0.01")) <= 0,
                () -> "spread was " + max.subtract(min) + " for " + participants + " participants");
    }

    @Test
    @DisplayName("equal split is deterministic, so replaying an event reproduces the same shares")
    void equalSplitIsDeterministic() {
        for (int i = 0; i < 20; i++) {
            assertEquals(
                    ShareAllocator.allocateEqually(new BigDecimal("77.77"), 6),
                    ShareAllocator.allocateEqually(new BigDecimal("77.77"), 6));
        }
    }

    @Test
    void weightedSplitAlsoSumsExactly() {
        List<BigDecimal> parts = ShareAllocator.allocateByWeight(
                new BigDecimal("100.00"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertEquals(0, sum(parts).compareTo(new BigDecimal("100.00")));
        assertEquals(List.of(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33")), parts);
    }

    @Test
    @DisplayName("leftover units go to whoever was rounded down hardest, not to whoever is first")
    void weightedSplitFavoursLargestRemainder() {
        // Entitlements are 16.66..., 33.33..., 50.00 -> remainders .66, .33, .00
        List<BigDecimal> parts = ShareAllocator.allocateByWeight(
                new BigDecimal("100.00"),
                List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3)));

        assertEquals(0, sum(parts).compareTo(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("16.67"), parts.get(0));
        assertEquals(new BigDecimal("33.33"), parts.get(1));
        assertEquals(new BigDecimal("50.00"), parts.get(2));
    }

    @Test
    void rejectsNonPositiveParticipantCount() {
        assertThrows(IllegalArgumentException.class,
                () -> ShareAllocator.allocateEqually(new BigDecimal("10.00"), 0));
    }

    @Test
    void rejectsZeroWeightSum() {
        assertThrows(IllegalArgumentException.class,
                () -> ShareAllocator.allocateByWeight(new BigDecimal("10.00"), List.of(BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("sub-paisa input is a caller bug and is rejected rather than silently truncated")
    void rejectsSubMinorUnitPrecision() {
        assertThrows(ArithmeticException.class,
                () -> ShareAllocator.allocateEqually(new BigDecimal("10.005"), 2));
    }
}
