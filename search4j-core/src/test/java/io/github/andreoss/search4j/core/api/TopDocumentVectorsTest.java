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
 * Checks the two best documents of each search against those captured from
 * the reference library.
 *
 * @since 0.5.2
 */
final class TopDocumentVectorsTest {

    @Test
    void matchesCapturedTopDocuments() throws IOException {
        final TopDocumentVectors vectors = new TopDocumentVectors();
        Assertions.assertEquals(
            List.of(List.of(), 211),
            List.of(vectors.check(), vectors.replayed().size()),
            "Documents, order and weights of the two best documents"
        );
    }

    @Test
    void matchesCorrectedTopDocumentsOfEveryDeferredSearch() throws IOException {
        final TopDocumentVectors vectors = new TopDocumentVectors();
        Assertions.assertEquals(
            List.of(List.of(), vectors.deferred()),
            List.of(vectors.checkCorrected(), vectors.replayed()),
            "Documents, order and weights of the two best documents"
        );
    }

    @Test
    void defersOnlySearchOfTheOptionalDecayTarget() throws IOException {
        final TopDocumentVectors vectors = new TopDocumentVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(Set.of("bm25\tm42"), Set.of("bm25\tm42"), Set.of()),
            List.of(
                vectors.deferred("decay-target"), vectors.skipped(),
                vectors.deferred("unstarted-difference")
            ),
            "Search whose captured best documents skip one matching both sides"
        );
    }
}
