/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the UTF-8 utilities.
 */
package io.github.andreoss.search4j.core.unicode;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link UnicodeData}.
 *
 * @since 0.1.0
 */
final class UnicodeDataTest {

    @Test
    void lowercasesUpper() {
        Assertions.assertEquals(
            'a', UnicodeData.lowercase('A'), "Lowercased"
        );
    }

    @Test
    void uppercasesLower() {
        Assertions.assertEquals(
            'A', UnicodeData.uppercase('a'), "Uppercased"
        );
    }

    @Test
    void letterIsAlphabetic() {
        Assertions.assertTrue(
            UnicodeData.alphabetic('x'), "Letter is alphabetic"
        );
    }
}
