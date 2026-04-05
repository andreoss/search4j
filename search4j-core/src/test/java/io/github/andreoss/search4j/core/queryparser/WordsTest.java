/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for the word splitting of the query parser.
 */
package io.github.andreoss.search4j.core.queryparser;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the word splitting of the query parser.
 *
 * @since 0.1.0
 */
final class WordsTest {

    @Test
    void splitsOnWhitespace() {
        Assertions.assertEquals(
            List.of("hello", "world"), Words.parts("hello world")
        );
    }

    @Test
    void dropsPunctuation() {
        Assertions.assertEquals(
            List.of("hello", "world"), Words.parts("hello, world!")
        );
    }

    @Test
    void keepsHyphenatedWords() {
        Assertions.assertEquals(
            List.of("state-of-the-art"), Words.parts("state-of-the-art")
        );
    }

    @Test
    void keepsUnderscoresInsideWords() {
        Assertions.assertEquals(
            List.of("field_name", "value"), Words.parts("field_name value")
        );
    }

    @Test
    void keepsDigits() {
        Assertions.assertEquals(
            List.of("test123", "abc456"), Words.parts("test123 abc456")
        );
    }

    @Test
    void ignoresEmptyText() {
        Assertions.assertTrue(Words.parts("").isEmpty());
    }

    @Test
    void normalisesToLowerCase() {
        Assertions.assertEquals("hello", Words.normalise("HeLLo"));
    }

    @Test
    void splitsPhrases() {
        Assertions.assertEquals(2, Words.phrase("one two").length);
    }

    @Test
    void keepsFirstPhraseTerm() {
        Assertions.assertEquals("one", Words.phrase("one two")[0]);
    }

    @Test
    void keepsSecondPhraseTerm() {
        Assertions.assertEquals("two", Words.phrase("one two")[1]);
    }
}
