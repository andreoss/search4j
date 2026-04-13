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
    void defersOnlyCapturedSearchesOfTheOptionalDecayDefect() throws IOException {
        final EnquireVectors vectors = new EnquireVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(11, vectors.deferred()),
            List.of(vectors.deferred().size(), vectors.skipped()),
            "Deferred searches exist among the captured ones"
        );
    }
}
