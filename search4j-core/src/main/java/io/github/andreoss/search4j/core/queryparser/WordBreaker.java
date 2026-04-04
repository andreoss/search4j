/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser word breaking utilities.
 */
package io.github.andreoss.search4j.core.queryparser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Unicode word breaker for query parsing.
 *
 * <p>Breaks text into words according to Unicode word boundaries,
 * suitable for query parsing.</p>
 *
 * @since 0.1.0
 */
public final class WordBreaker {

    /**
     * Utility class, no instances.
     */
    private WordBreaker() {}

    /**
     * Breaks a string into words at Unicode word boundaries.
     *
     * @param text Input text
     * @return List of word strings
     */
    public static List<String> breakWords(final String text) {
        final List<String> words = new ArrayList<>();
        final int length = text.length();
        int start = 0;
        int end = 0;

        while (end < length) {
            final char c = text.charAt(end);
            if (isWordChar(c)) {
                end++;
            } else {
                if (end > start) {
                    words.add(text.substring(start, end));
                }
                // Skip non-word characters
                while (end < length && !isWordChar(text.charAt(end))) {
                    end++;
                }
                start = end;
            }
        }

        if (end > start) {
            words.add(text.substring(start, end));
        }

        return words;
    }

    /**
     * Tests whether a character is a word character.
     *
     * <p>A word character is a letter, digit, or underscore-like character.</p>
     *
     * @param c Character to test
     * @return True if word character
     */
    private static boolean isWordChar(final char c) {
        return Character.isLetterOrDigit(c)
            || c == '_'
            || c == '-'
            || c == '.'
            || Character.getType(c) == Character.NON_SPACING_MARK
            || Character.getType(c) == Character.COMBINING_SPACING_MARK;
    }

    /**
     * Normalises a word for query processing.
     *
     * @param word Word to normalise
     * @return Normalised word (lowercased)
     */
    public static String normalise(final String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    /**
     * Splits a phrase into individual terms.
     *
     * @param phrase Phrase string
     * @return Array of terms
     */
    public static String[] splitPhrase(final String phrase) {
        return breakWords(phrase).toArray(new String[0]);
    }
}
