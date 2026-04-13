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
import java.util.regex.Pattern;
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
            List.of(), new WeightVectors().check("PRUVG"),
            "Parameter parsing, names and serialised parameters"
        );
    }

    @Test
    void matchesCapturedWeightsOfUnknownNormalisationCodes() throws IOException {
        final WeightVectors vectors = new WeightVectors(
            Pattern.compile(
                "@tfidf 9a9999999999c93f000000000000f03f(000201|0c0201|ff0201|010001|010b01|01ff01|010200|0102ff|808080|050c01|0a0b02)"
            ).asMatchPredicate()
        );
        Assertions.assertEquals(
            List.of(List.of(), 1568),
            List.of(vectors.check("SMDC"), vectors.replayed()),
            "Parts and document weights for codes outside the normalisations"
        );
    }

    @Test
    void matchesCapturedPartsAboveTheSignedRange() throws IOException {
        final WideVectors vectors = new WideVectors();
        Assertions.assertEquals(
            List.of(List.of(), 114),
            List.of(vectors.check(), vectors.replayed()),
            "Divergence-from-randomness parts for counts above 2147483647"
        );
    }

    @Test
    void capturedEveryKindOfVector() throws IOException {
        final Map<String, Integer> kinds = new HashMap<>(16);
        for (final String[] row : WeightVectors.rows("weight-vectors.tsv")) {
            kinds.merge(row[0], 1, Integer::sum);
        }
        Assertions.assertEquals(
            11, kinds.size(), String.format("Kinds %s", kinds)
        );
    }
}
