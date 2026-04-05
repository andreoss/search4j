/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser tokeniser support.
 */
package io.github.andreoss.search4j.core.queryparser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Breaks text into words for query parsing.
 *
 * <p>A word is a run of letters, digits and combining marks; underscores,
 * hyphens and full stops continue a word once it has started.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Words {

    /**
     * Utility class, no instances.
     */
    private Words() {
    }

    /**
     * Breaks a string into words.
     *
     * @param text Text to break
     * @return Words in the order they appear
     */
    public static List<String> parts(final String text) {
        final List<String> found = new ArrayList<>(1);
        final int length = text.length();
        int start = 0;
        int index = 0;
        while (index < length) {
            if (Words.word(text.charAt(index))) {
                index = index + 1;
            } else {
                if (index > start) {
                    found.add(text.substring(start, index));
                }
                while (index < length && !Words.word(text.charAt(index))) {
                    index = index + 1;
                }
                start = index;
            }
        }
        if (index > start) {
            found.add(text.substring(start, index));
        }
        return found;
    }

    /**
     * Normalises a word for query processing.
     *
     * @param word Word to normalise
     * @return Word in lower case
     */
    public static String normalise(final String word) {
        return word.toLowerCase(Locale.ROOT);
    }

    /**
     * Splits a phrase into individual terms.
     *
     * @param phrase Phrase to split
     * @return Terms in the order they appear
     */
    public static String[] phrase(final String phrase) {
        return Words.parts(phrase).toArray(new String[0]);
    }

    private static boolean word(final char character) {
        return Character.isLetterOrDigit(character)
            || Words.point(character)
            || Character.getType(character) == Character.NON_SPACING_MARK;
    }

    private static boolean point(final char character) {
        return character == '_' || character == '-' || character == '.';
    }
}
