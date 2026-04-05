/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for the word breaker of the term generator.
 */
package io.github.andreoss.search4j.core.termgen;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the word breaker of the term generator.
 *
 * @since 0.1.0
 */
final class WordBreakerTest {

    @Test
    void splitsWordsOnWhitespace() {
        Assertions.assertEquals(2, WordBreakerTest.texts("hello world").size());
    }

    @Test
    void keepsFirstWord() {
        Assertions.assertEquals("hello", WordBreakerTest.texts("hello world").get(0));
    }

    @Test
    void keepsSecondWord() {
        Assertions.assertEquals("world", WordBreakerTest.texts("hello world").get(1));
    }

    @Test
    void keepsApostrophesInsideWords() {
        Assertions.assertEquals("don't", WordBreakerTest.texts("don't stop").get(0));
    }

    @Test
    void keepsHyphensInsideWords() {
        Assertions.assertEquals("well-known", WordBreakerTest.texts("well-known").get(0));
    }

    @Test
    void dropsTrailingHyphen() {
        Assertions.assertEquals("well", WordBreakerTest.texts("well- known").get(0));
    }

    @Test
    void splitsAfterTrailingHyphen() {
        Assertions.assertEquals("known", WordBreakerTest.texts("well- known").get(1));
    }

    @Test
    void dropsLeadingApostrophe() {
        Assertions.assertEquals("quoted", WordBreakerTest.texts("'quoted'").get(0));
    }

    @Test
    void marksSentenceEnd() {
        Assertions.assertTrue(WordBreaker.words("One. Two").get(0).sentenceEnd());
    }

    @Test
    void keepsLastWordOpenWithoutTerminator() {
        Assertions.assertFalse(WordBreaker.words("One. Two").get(1).sentenceEnd());
    }

    @Test
    void ignoresEmptyText() {
        Assertions.assertTrue(WordBreaker.words("").isEmpty());
    }

    @Test
    void ignoresPunctuationOnly() {
        Assertions.assertTrue(WordBreaker.words(" ... !!! ").isEmpty());
    }

    @Test
    void keepsDigits() {
        Assertions.assertEquals("abc123", WordBreakerTest.texts("abc123").get(0));
    }

    @Test
    void keepsCombiningMarks() {
        final String text = WordBreakerTest.accents();
        Assertions.assertEquals(text, WordBreakerTest.texts(text).get(0));
    }

    @Test
    void recognisesLetterParts() {
        Assertions.assertTrue(WordBreaker.part('a'));
    }

    @Test
    void recognisesDigitParts() {
        Assertions.assertTrue(WordBreaker.part('7'));
    }

    @Test
    void rejectsSpaces() {
        Assertions.assertFalse(WordBreaker.part(' '));
    }

    @Test
    void joinsHyphensBetweenParts() {
        Assertions.assertEquals(1, WordBreaker.words("a-b").size());
    }

    @Test
    void joinsApostrophesBetweenParts() {
        Assertions.assertEquals(1, WordBreaker.words("a'b").size());
    }

    @Test
    void doesNotJoinLeadingHyphens() {
        Assertions.assertEquals("ab", WordBreakerTest.texts("-ab").get(0));
    }

    @Test
    void doesNotJoinTrailingHyphens() {
        Assertions.assertEquals("ab", WordBreakerTest.texts("ab-").get(0));
    }

    @Test
    void doesNotJoinSpaces() {
        Assertions.assertEquals(2, WordBreaker.words("a b").size());
    }

    @Test
    void readsSentencesEndingInQuotedTerminator() {
        Assertions.assertTrue(WordBreaker.endsSentence("word.' next", 4));
    }

    @Test
    void readsSentencesEndingInQuestionMark() {
        Assertions.assertTrue(WordBreaker.endsSentence("word? next", 4));
    }

    @Test
    void readsOpenSentences() {
        Assertions.assertFalse(WordBreaker.endsSentence("word next", 4));
    }

    private static String accents() {
        return new String(new char[] {'e', 0x0301, 't', 'u', 'd', 'e'});
    }

    private static List<String> texts(final String text) {
        final List<String> found = new ArrayList<>(0);
        for (final WordBreaker.Word word : WordBreaker.words(text)) {
            found.add(word.text());
        }
        return found;
    }
}
