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
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks positional, synonym, maximum, elite set, wildcard and edit distance
 * searches against match sets captured from the reference library.
 *
 * @since 0.10.0
 */
final class PositionalVectorsTest {

    @Test
    void describesEveryCapturedQuery() throws IOException {
        Assertions.assertEquals(
            List.of(), new PositionalVectors().descriptions(),
            "Query descriptions and construction errors"
        );
    }

    @Test
    void matchesCapturedMatchSets() throws IOException {
        Assertions.assertEquals(
            List.of(), new PositionalVectors().check(),
            "Documents, order, weights, item counts, bounds and errors"
        );
    }

    @Test
    void matchesCapturedMatchSetsUnderMoreSchemes() throws IOException {
        final PositionalVectors vectors = new PositionalVectors(
            "positional-scheme-vectors.tsv", ""
        );
        Assertions.assertEquals(
            List.of(List.of(), 9_975),
            List.of(vectors.check(), vectors.replayed().size()),
            "Synonym and expansion searches under parameterised schemes"
        );
    }

    @Test
    void replaysEveryCapturedSearch() throws IOException {
        final PositionalVectors vectors = new PositionalVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(12_460, 4),
            List.of(vectors.replayed().size(), vectors.skipped().size()),
            "Searches replayed and searches left to the corrected vectors"
        );
    }

    @Test
    void matchesCorrectedMatchSetsOfTheOptionalDecayTarget() throws IOException {
        final PositionalVectors vectors = new PositionalVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(
                List.of(),
                Set.of(
                    "bm25\tp046\t0,1,0,0,0", "bm25\tp072\t0,3,0,0,0",
                    "bm25\tp072\t0,3,0,30,0", "bm25\tp072\t1,2,0,0,0"
                )
            ),
            List.of(vectors.checkCorrected(), vectors.skipped()),
            "Searches whose top documents skip one matching both sides"
        );
    }
}
