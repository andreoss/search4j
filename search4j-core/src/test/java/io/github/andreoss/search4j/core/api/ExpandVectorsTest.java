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
 * Checks expansion sets against those captured from the reference library
 * under the default and an explicit matching scheme and expansion scheme.
 *
 * @since 0.5.4
 */
final class ExpandVectorsTest {

    @Test
    void matchesCapturedExpansionSets() throws IOException {
        final ExpandVectors vectors = new ExpandVectors();
        Assertions.assertEquals(
            List.of(List.of(), 7632),
            List.of(vectors.check(), vectors.expansions()),
            "Terms, order and weights"
        );
    }

    @Test
    void replaysExpansionsOfTheOptionalDecayTargetAgainstCorrectedSets()
        throws IOException {
        final ExpandVectors vectors = new ExpandVectors();
        vectors.check();
        Assertions.assertEquals(
            List.of(12, true),
            List.of(
                vectors.corrected().size(),
                vectors.corrected().stream().allMatch(key -> key.contains("\tm42\t"))
            ),
            "Expansions whose relevant documents skip one matching both sides"
        );
    }
}
