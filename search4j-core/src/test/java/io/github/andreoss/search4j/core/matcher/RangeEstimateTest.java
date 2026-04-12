/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Formula checks for the boolean match-estimate tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.types.TermCount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the static estimates of value ranges.
 *
 * @since 0.3.0
 */
final class RangeEstimateTest {

    @Test
    void spreadsValuesEvenlyBetweenBounds() {
        Assertions.assertEquals(
            3L,
            RangeEstimate.frequency(RangeEstimateTest.stats(8, "a", "h"), "b", "e"),
            "Three sevenths of eight values"
        );
    }

    @Test
    void countsEveryValueWhenAllValuesAreEqual() {
        Assertions.assertEquals(
            5L,
            RangeEstimate.frequency(RangeEstimateTest.stats(5, "x", "x"), "a", "z"),
            "A slot holding one value"
        );
    }

    @Test
    void halvesWhenBoundsDifferOnlyByTrailingZeros() {
        Assertions.assertEquals(
            3L,
            RangeEstimate.frequency(
                RangeEstimateTest.stats(6, "a", "a".concat(String.valueOf((char) 0))),
                "a", "a"
            ),
            "Bounds with the same fraction"
        );
    }

    @Test
    void boundsPartialRangeByValueFrequency() {
        final MatchEstimates found = RangeEstimate.of(
            RangeEstimateTest.stats(8, "a", "h"), "d", ""
        );
        Assertions.assertEquals(
            "0 5 8",
            String.format(
                "%d %d %d", found.min().value(), found.est().value(),
                found.max().value()
            ),
            "Unknown minimum, spread estimate, value frequency maximum"
        );
    }

    @Test
    void countsCoveringRangeExactly() {
        Assertions.assertEquals(
            new TermCount(8),
            RangeEstimate.of(RangeEstimateTest.stats(8, "b", "g"), "a", "h").min(),
            "Every value is in the range"
        );
    }

    @Test
    void estimatesNothingOutsideBounds() {
        Assertions.assertEquals(
            TermCount.ZERO,
            RangeEstimate.of(RangeEstimateTest.stats(8, "b", "g"), "h", "z").max(),
            "Range above every value"
        );
    }

    @Test
    void estimatesNothingBelowBounds() {
        Assertions.assertEquals(
            TermCount.ZERO,
            RangeEstimate.of(RangeEstimateTest.stats(8, "b", "g"), "", "a").max(),
            "Range below every value"
        );
    }

    @Test
    void estimatesNothingForEmptySlot() {
        Assertions.assertEquals(
            TermCount.ZERO,
            RangeEstimate.of(ValueStats.NONE, "a", "z").est(),
            "Slot without values"
        );
    }

    private static ValueStats stats(final int freq, final String lower, final String upper) {
        return new ValueStats(new TermCount(freq), lower, upper);
    }
}
