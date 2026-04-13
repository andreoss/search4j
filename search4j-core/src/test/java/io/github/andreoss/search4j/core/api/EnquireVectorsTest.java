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
import java.util.TreeSet;
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

    @Test
    void matchesCapturedMatchSetsUnderDocumentOrderAndCollapsing()
        throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        Assertions.assertEquals(
            List.of(List.of(), 26_712),
            List.of(vectors.checkOptions(), vectors.searches()),
            "Documents, order, weights, item counts and bounds"
        );
    }

    @Test
    void matchesCapturedCollapseCountsKeysAndUncollapsedBounds()
        throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        Assertions.assertEquals(
            List.of(List.of(), 26_712),
            List.of(vectors.checkCollapse(), vectors.searches()),
            "Collapse counts, collapse keys and bounds before collapsing"
        );
    }

    @Test
    void replaysCollapseSearchesOfTheOptionalDecayTargetAgainstCorrectedSets()
        throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.checkCollapse();
        final Set<String> queries = new TreeSet<>();
        for (final String search : vectors.corrected()) {
            queries.add(
                search.substring(search.indexOf('\t') + 1, search.lastIndexOf('\t'))
            );
        }
        Assertions.assertEquals(
            List.of(41, vectors.corrected(), Set.of("m42", "m73")),
            List.of(vectors.corrected().size(), vectors.skipped(), queries),
            "Captured searches whose counted matches skip one matching both sides"
        );
    }

    @Test
    void replaysOptionSearchesOfTheOptionalDecayTargetAgainstCorrectedSets()
        throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.checkOptions();
        final Set<String> queries = new TreeSet<>();
        for (final String search : vectors.corrected()) {
            queries.add(
                search.substring(search.indexOf('\t') + 1, search.lastIndexOf('\t'))
            );
        }
        Assertions.assertEquals(
            List.of(34, vectors.corrected(), Set.of("m42", "m73")),
            List.of(vectors.corrected().size(), vectors.skipped(), queries),
            "Captured searches whose top documents skip one matching both sides"
        );
    }
}
