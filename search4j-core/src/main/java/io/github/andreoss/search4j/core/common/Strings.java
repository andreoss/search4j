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
     * @param text Text to test, may be null
     * @return True for null or the empty string
     */
    public static boolean empty(final String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Replaces null with the empty string.
     *
     * @param text Text to normalise, may be null
     * @return The text, or the empty string for null
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
