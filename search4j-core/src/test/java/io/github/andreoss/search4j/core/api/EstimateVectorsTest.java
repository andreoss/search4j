/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the reported match estimates over a corpus large enough for wide
 * bounds against those captured from the reference library.
 *
 * @since 0.7.1
 */
final class EstimateVectorsTest {

    @Test
    void matchesCapturedEstimatesAndMatches() throws IOException {
        Assertions.assertEquals(
            List.of(), new EstimateVectors().check(),
            "Documents, weights, collapse counts and keys, bounds and estimates"
        );
    }

    @Test
    void replaysSearchesWhereRoundingChangesTheEstimate() throws IOException {
        final EstimateVectors vectors = new EstimateVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(704, 559, 625),
            List.of(
                vectors.tally("searches"), vectors.tally("wide"),
                vectors.tally("rounded")
            ),
            "Searches, searches with bounds more than ten apart, rounded estimates"
        );
    }

    @Test
    void roundsCapturedUnroundedEstimatesAsReported() throws IOException {
        Assertions.assertEquals(
            List.of(), new EstimateVectors().rounding(),
            "Rounding of the unrounded estimates the reference holds"
        );
    }
}
