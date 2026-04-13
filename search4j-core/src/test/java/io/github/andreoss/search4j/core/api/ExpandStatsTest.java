/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the statistics of a term over the relevant documents.
 *
 * @since 0.5.4
 */
final class ExpandStatsTest {

    @Test
    void countsOneShareForEachIndexedDocumentByDefault() {
        Assertions.assertEquals(
            3.0,
            new ExpandStats(new TermCount(7), new TermCount(3), new TermCount(5))
                .multiplier(),
            "Multiplier of three relevant documents indexed by the term"
        );
    }

    @Test
    void sharesOneForEachDocumentUnlessTheSchemeWeighsIt() {
        Assertions.assertEquals(
            1.0,
            new Bo1EWeight().share(new TermCount(4), new TermCount(9), 3.0),
            "Default share"
        );
    }
}
