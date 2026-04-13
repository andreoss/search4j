/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the rounding of a bounded estimate against lower bound, upper
 * bound, estimate and rounded estimate rows captured from the reference
 * rounding, over listed, exhaustive small and pseudo-random bounds.
 *
 * @since 0.7.1
 */
final class RoundedEstimateTest {

    @Test
    void roundsAsTheReference() throws IOException {
        final List<String> failures = new ArrayList<>(0);
        for (final String[] row : WeightVectors.rows("estimate-rounding-vectors.tsv")) {
            final int actual = new RoundedEstimate(
                RoundedEstimateTest.count(row[0]), RoundedEstimateTest.count(row[2]),
                RoundedEstimateTest.count(row[1])
            ).value().value();
            if (actual != Integer.parseInt(row[3])) {
                failures.add(String.format("%s got %d", String.join(" ", row), actual));
            }
        }
        Assertions.assertEquals(List.of(), failures, "Rounded estimates");
    }

    @Test
    void readsEveryCapturedRow() throws IOException {
        int rows = 0;
        int changed = 0;
        for (final String[] row : WeightVectors.rows("estimate-rounding-vectors.tsv")) {
            rows = rows + 1;
            if (!row[2].equals(row[3])) {
                changed = changed + 1;
            }
        }
        Assertions.assertEquals(
            List.of(8564, 6064), List.of(rows, changed),
            "Rows captured, rows whose estimate the rounding changes"
        );
    }

    @Test
    void keepsTheBoundsOfAMatchSetUnrounded() {
        final MSet found = new MSetImpl(
            List.of(), new TermCount(11), new TermCount(1234), new TermCount(5678),
            new TermCount(3), new TermCount(4321), new TermCount(9876)
        );
        Assertions.assertEquals(
            List.of(11, 1000, 5678, 3, 4000, 9876),
            List.of(
                found.matchesLowerBound().value(), found.matchesEstimated().value(),
                found.matchesUpperBound().value(),
                found.uncollapsedMatchesLowerBound().value(),
                found.uncollapsedMatchesEstimated().value(),
                found.uncollapsedMatchesUpperBound().value()
            ),
            "Bounds as held, estimates rounded"
        );
    }

    private static TermCount count(final String text) {
        return new TermCount(Integer.parseInt(text));
    }
}
