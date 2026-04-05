/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term generation from text: words, prefixes, positions and n-grams.
 */
package io.github.andreoss.search4j.core.termgen;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into words and marks whether each one ends a sentence.
 *
 * <p>A word is a run of letters, digits and combining marks; an apostrophe or
 * a hyphen joins two such runs when both sides are non-empty.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "PMD.AvoidBranchingStatementAsLastInLoop"
})
public final class WordBreaker {

    /**
     * Longest word kept, in characters.
     */
    public static final int MAX_WORD = 64;

    private WordBreaker() {
    }

    /**
     * Splits text into words.
     *
     * @param text Text to split
     * @return Words in the order they appear
     */
    public static List<Word> words(final String text) {
        final List<Word> words = new ArrayList<>(1);
        final int length = text.length();
        int start = -1;
        for (int index = 0; index <= length; ++index) {
            final boolean atcap = index == length;
            final boolean joined = !atcap && WordBreaker.joinable(text, index);
            final boolean part = !atcap && WordBreaker.part(text.charAt(index));
            if (start < 0) {
                if (part) {
                    start = index;
                }
            } else if (!part && !joined) {
                words.add(
                    new Word(
                        text.substring(start, index),
                        WordBreaker.endsSentence(text, index)
                    )
                );
                start = -1;
            }
        }
        return words;
    }

    static boolean part(final char character) {
        return Character.isLetterOrDigit(character)
            || WordBreaker.mark(Character.getType(character));
    }

    static boolean endsSentence(final String text, final int index) {
        boolean result = false;
        for (int scan = index; scan < text.length(); ++scan) {
            final char character = text.charAt(scan);
            if (character == '\'' || character == '"') {
                continue;
            }
            result = WordBreaker.stop(character)
                || character == 0x3002 || character == 0xFF01
                || character == 0xFF1F;
            break;
        }
        return result;
    }

    private static boolean mark(final int kind) {
        return kind == Character.NON_SPACING_MARK
            || kind == Character.COMBINING_SPACING_MARK;
    }

    private static boolean joiner(final char character) {
        return character == 0x27 || character == '-';
    }

    private static boolean stop(final char character) {
        return character == '.' || character == '!' || character == '?';
    }

    private static boolean both(final String text, final int index) {
        return index + 1 < text.length()
            && WordBreaker.part(text.charAt(index - 1))
            && WordBreaker.part(text.charAt(index + 1));
    }

    private static boolean joinable(final String text, final int index) {
        final boolean result;
        if (WordBreaker.joiner(text.charAt(index)) && index > 0) {
            result = WordBreaker.both(text, index);
        } else {
            result = false;
        }
        return result;
    }

    /**
     * A word and whether it ends a sentence.
     *
     * @param text Characters of the word
     * @param sentenceEnd True when the word ends a sentence
     * @since 0.1.0
     */
    public record Word(String text, boolean sentenceEnd) {
    }
}
