/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Common utilities of the search engine.
 */
package io.github.andreoss.search4j.core.common;

/**
 * String helpers used by the common layer.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Strings {

    /**
     * Utility class, no instances.
     */
    private Strings() {
    }

    /**
     * Tests whether a text is null or empty.
     *
     * @param text The text to check
     * @return True when the text is null or empty
     */
    public static boolean empty(final String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Returns the text unchanged, or an empty string when null.
     *
     * @param text The text to normalise
     * @return The non-null text
     */
    public static String orEmpty(final String text) {
        final String result;
        if (text == null) {
            result = "";
        } else {
            result = text;
        }
        return result;
    }
}
