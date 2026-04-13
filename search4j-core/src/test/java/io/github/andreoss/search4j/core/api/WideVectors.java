/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.TermWeight;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays divergence-from-randomness parts captured from the reference
 * library for counts above the signed 32-bit range.
 *
 * @since 0.5.0
 */
final class WideVectors {

    /**
     * Failures found.
     */
    private final List<String> failures;

    /**
     * Number of rows replayed.
     */
    private int rows;

    WideVectors() {
        this.failures = new ArrayList<>(0);
    }

    List<String> check() throws IOException {
        for (final String[] row : WeightVectors.rows("weight-wide-vectors.tsv")) {
            this.rows = this.rows + 1;
            final TermWeight part = WideVectors.part(row);
            final double actual;
            final String expected;
            if ("M".equals(row[0])) {
                actual = part.maxPart();
                expected = row[17];
            } else {
                actual = WideVectors.sum(
                    part, Double.parseDouble(row[17]), Double.parseDouble(row[18])
                );
                expected = row[21];
            }
            this.close(expected, actual, row);
        }
        return this.failures;
    }

    int replayed() {
        return this.rows;
    }

    private void close(final String text, final double actual, final String... row) {
        final double expected = Double.parseDouble(text);
        if (Math.abs(expected - actual)
            > 1e-9 * Math.max(Math.abs(expected), Math.abs(actual))
            || Double.isNaN(actual)) {
            this.failures.add(
                String.format(
                    "%s expected %s got %s", String.join(" ", row), text, actual
                )
            );
        }
    }

    private static TermWeight part(final String... row) {
        final DfrStats input = new DfrStats(
            Double.parseDouble(row[16]), Double.parseDouble(row[9]),
            Double.parseDouble(row[8]),
            Double.parseDouble(row[5]) / Double.parseDouble(row[14]),
            Double.parseDouble(row[15]) * Double.parseDouble(row[2])
        );
        final TermWeight part;
        if ("dph".equals(row[1])) {
            part = DphPart.of(input);
        } else {
            part = DlhPart.of(input);
        }
        return part;
    }

    private static double sum(final TermWeight part, final double count, final double len) {
        final double result;
        if (part instanceof DphPart dph) {
            result = dph.sum(count, len);
        } else {
            result = ((DlhPart) part).sum(count, len);
        }
        return result;
    }
}
