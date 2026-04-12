/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks every weighting scheme against vectors captured from the reference
 * library over a shared corpus.
 *
 * @since 0.1.0
 */
final class WeightVectorsTest {

    @Test
    void matchesCapturedPartVectors() throws IOException {
        Assertions.assertEquals(
            List.of(), new WeightVectors().check("SMEX"),
            "Term and extra parts for the statistics the reference passed"
        );
    }

    @Test
    void matchesCapturedDocumentWeights() throws IOException {
        Assertions.assertEquals(
            List.of(), new WeightVectors().check("DC"),
            "Documents, order and weights of whole searches"
        );
    }

    @Test
    void matchesCapturedParametersAndBytes() throws IOException {
        Assertions.assertEquals(
            List.of(), new WeightVectors().check("PRUV"),
            "Parameter parsing, names and serialised parameters"
        );
    }

    @Test
    void capturedEveryKindOfVector() throws IOException {
        final Map<String, Integer> kinds = new HashMap<>(16);
        for (final String[] row : WeightVectors.rows("weight-vectors.tsv")) {
            kinds.merge(row[0], 1, Integer::sum);
        }
        Assertions.assertEquals(
            10, kinds.size(), String.format("Kinds %s", kinds)
        );
    }
}
