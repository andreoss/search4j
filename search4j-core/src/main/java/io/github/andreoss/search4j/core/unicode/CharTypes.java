/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * UTF-8 and character classification utilities of the search engine.
 */
package io.github.andreoss.search4j.core.unicode;

/**
 * Character classification used by the tokeniser.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class CharTypes {

    /**
     * Utility class, no instances.
     */
    private CharTypes() {
    }

    /**
     * Tests whether a code point is a word character.
     *
     * @param codepoint The code point
     * @return True when a letter, digit or connector is present
     */
    public static boolean word(final int codepoint) {
        return Character.isLetterOrDigit(codepoint)
            || Character.getType(codepoint) == Character.CONNECTOR_PUNCTUATION;
    }

    /**
     * Tests whether a code point is whitespace.
     *
     * @param codepoint The code point
     * @return True when whitespace
     */
    public static boolean space(final int codepoint) {
        return Character.isWhitespace(codepoint);
    }

    /**
     * Tests whether a code point is a decimal digit.
     *
     * @param codepoint The code point
     * @return True when a decimal digit
     */
    public static boolean digit(final int codepoint) {
        return Character.isDigit(codepoint);
    }

    /**
     * Tests whether a code point is an uppercase letter.
     *
     * @param codepoint The code point
     * @return True when uppercase
     */
    public static boolean upper(final int codepoint) {
        return Character.isUpperCase(codepoint);
    }
}
