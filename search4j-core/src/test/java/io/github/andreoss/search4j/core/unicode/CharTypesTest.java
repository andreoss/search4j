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
 * Tests of {@link CharTypes}.
 *
 * @since 0.1.0
 */
final class CharTypesTest {

    @Test
    void letterIsWord() {
        Assertions.assertTrue(CharTypes.word('a'), "Letter is word");
    }

    @Test
    void digitIsWord() {
        Assertions.assertTrue(CharTypes.word('7'), "Digit is word");
    }

    @Test
    void spaceIsNotWord() {
        Assertions.assertFalse(CharTypes.word(' '), "Space is not word");
    }

    @Test
    void spaceIsWhitespace() {
        Assertions.assertTrue(CharTypes.space(' '), "Space is whitespace");
    }

    @Test
    void digitIsDigit() {
        Assertions.assertTrue(CharTypes.digit('3'), "Digit recognised");
    }

    @Test
    void upperIsUpper() {
        Assertions.assertTrue(CharTypes.upper('Z'), "Uppercase recognised");
    }
}
