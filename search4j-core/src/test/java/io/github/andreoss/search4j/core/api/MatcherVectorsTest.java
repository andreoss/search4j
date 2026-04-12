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
 * Checks the matcher against match vectors captured from the reference
 * library over the shared corpus with values.
 *
 * @since 0.3.0
 */
final class MatcherVectorsTest {

    @Test
    void matchesCapturedDocumentsAndWeights() throws IOException {
        Assertions.assertEquals(
            List.of(), new MatcherVectors().check("D"),
            "Documents, order and weights of whole searches"
        );
    }

    @Test
    void matchesCapturedCounts() throws IOException {
        Assertions.assertEquals(
            List.of(), new MatcherVectors().check("C"),
            "Number of matching documents"
        );
    }

    @Test
    void matchesCapturedEstimates() throws IOException {
        Assertions.assertEquals(
            List.of(), new MatcherVectors().check("E"),
            "Lower bound, estimate and upper bound before matching"
        );
    }

    @Test
    void keepsCapturedTopDocumentsWhenPruning() throws IOException {
        Assertions.assertEquals(
            List.of(), new MatcherVectors().check("K"),
            "Best documents found when skipping below their minimum weight"
        );
    }
}
