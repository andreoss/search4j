/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for WordBreaker.
 */
package io.github.andreoss.search4j.core.queryparser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WordBreaker.
 */
class WordBreakerTest {

    @Test
    void breakWordsSimple() {
        final List<String> words = WordBreaker.breakWords("hello world");
        assertEquals(List.of("hello", "world"), words);
    }

    @Test
    void breakWordsWithPunctuation() {
        final List<String> words = WordBreaker.breakWords("hello, world!");
        assertEquals(List.of("hello", "world"), words);
    }

    @Test
    void breakWordsWithHyphen() {
        final List<String> words = WordBreaker.breakWords("state-of-the-art");
        assertEquals(List.of("state-of-the-art"), words);
    }

    @Test
    void breakWordsWithUnderscore() {
        final List<String> words = WordBreaker.breakWords("field_name value");
        assertEquals(List.of("field_name", "value"), words);
    }

    @Test
    void breakWordsWithDigits() {
        final List<String> words = WordBreaker.breakWords("test123 abc456");
        assertEquals(List.of("test123", "abc456"), words);
    }

    @Test
    void breakWordsEmpty() {
        final List<String> words = WordBreaker.breakWords("");
        assertEquals(List.of(), words);
    }

    @Test
    void breakWordsOnlyWhitespace() {
        final List<String> words = WordBreaker.breakWords("   \t\n  ");
        assertEquals(List.of(), words);
    }

    @Test
    void breakWordsUnicode() {
        final List<String> words = WordBreaker.breakWords("café résumé naïve");
        assertEquals(List.of("café", "résumé", "naïve"), words);
    }

    @Test
    void normalise() {
        assertEquals("hello", WordBreaker.normalise("HELLO"));
        assertEquals("world", WordBreaker.normalise("World"));
        assertEquals("café", WordBreaker.normalise("CAFÉ"));
    }

    @Test
    void splitPhrase() {
        final String[] terms = WordBreaker.splitPhrase("hello world test");
        assertArrayEquals(new String[]{"hello", "world", "test"}, terms);
    }
}
