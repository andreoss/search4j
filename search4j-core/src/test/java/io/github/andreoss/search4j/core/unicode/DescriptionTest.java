/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the UTF-8 utilities.
 */
package io.github.andreoss.search4j.core.unicode;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests of {@link Description}.
 *
 * @since 0.1.0
 */
final class DescriptionTest {

    @ParameterizedTest
    @CsvSource({
        "abc, abc",
        "a\\b, a\\x5cb",
        "'', ''",
        "a b, a b",
        "~!, ~!"
    })
    void keepsPrintableText(final String text, final String expected) {
        Assertions.assertEquals(
            expected, Description.escape(text), "Printable characters stay"
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0x1F, 0x7F, 9, 10, 13})
    void escapesControlCharacters(final int point) {
        Assertions.assertEquals(
            String.format("a\\x%02xb", point),
            Description.escape(String.format("a%cb", (char) point)),
            "Two lowercase hexadecimal digits"
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0x20, 0x80, 0xE9, 0x20AC, 0x1F600})
    void keepsNonAsciiCharacters(final int point) {
        final String text = new String(Character.toChars(point));
        Assertions.assertEquals(
            text, Description.escape(text), "Code points at or above the space stay"
        );
    }
}
