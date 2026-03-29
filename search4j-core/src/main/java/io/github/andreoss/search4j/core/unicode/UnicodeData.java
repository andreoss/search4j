/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * UTF-8 and character classification utilities of the search engine.
 */
package io.github.andreoss.search4j.core.unicode;

/**
 * Case mapping and property lookups backed by the JDK Unicode tables.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class UnicodeData {

    /**
     * Utility class, no instances.
     */
    private UnicodeData() {
    }

    /**
     * Lowercases a code point.
     *
     * @param codepoint The code point
     * @return The lowercased code point
     */
    public static int lowercase(final int codepoint) {
        return Character.toLowerCase(codepoint);
    }

    /**
     * Uppercases a code point.
     *
     * @param codepoint The code point
     * @return The uppercased code point
     */
    public static int uppercase(final int codepoint) {
        return Character.toUpperCase(codepoint);
    }

    /**
     * Tests whether a code point is alphabetic.
     *
     * @param codepoint The code point
     * @return True when alphabetic
     */
    public static boolean alphabetic(final int codepoint) {
        return Character.isAlphabetic(codepoint);
    }
}
