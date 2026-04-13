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
 * Checks sorted and cut match sets against those captured from the
 * reference library for more matches than requested.
 *
 * @since 0.4.0
 */
final class EnquireVectorsTest {

    @Test
    void matchesCapturedMatchSets() throws IOException {
        Assertions.assertEquals(
            List.of(), new EnquireVectors().check(),
            "Documents, order, weights, item counts and bounds"
        );
    }

    @Test
    void replaysEveryCapturedSearch() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.check();
        Assertions.assertEquals(
            11_024, vectors.searches(), "Searches captured"
        );
    }

    @Test
    void matchesCorrectedMatchSetsOfEveryDeferredSearch() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        Assertions.assertEquals(
            List.of(List.of(), vectors.deferred()),
            List.of(vectors.checkCorrected(), vectors.corrected()),
            "Documents, order, weights, item counts and bounds"
        );
    }

    @Test
    void skipsExactlyTheDeferredSearches() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(11, vectors.deferred()),
            List.of(vectors.skipped().size(), vectors.skipped()),
            "Deferred searches exist among the captured ones"
        );
    }

    @Test
    void defersSearchesOfTheOptionalDecayTarget() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.check();
        final Set<String> decay = vectors.deferred("decay-target");
        Assertions.assertEquals(
            List.of(10, true),
            List.of(decay.size(), vectors.skipped().containsAll(decay)),
            "Searches whose captured top documents skip one matching both sides"
        );
    }

    @Test
    void defersSearchOfTheUnstartedDifference() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.check();
        final Set<String> unstarted = vectors.deferred("unstarted-difference");
        Assertions.assertEquals(
            List.of(Set.of("tfidf\tm70\t0,10,0,0,1,R"), true),
            List.of(unstarted, vectors.skipped().containsAll(unstarted)),
            "Search whose captured documents include an excluded one"
        );
    }
}
