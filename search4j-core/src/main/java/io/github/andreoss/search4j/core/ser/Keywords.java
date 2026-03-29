/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Keyword escaping for terms.
 *
 * <p>Non-alphanumeric bytes are percent-encoded so an arbitrary string forms
 * one safe keyword.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Keywords {

    /**
     * Utility class, no instances.
     */
    private Keywords() {
    }

    /**
     * Escapes a string into a keyword-safe form.
     *
     * @param text The text to escape
     * @return The escaped keyword
     */
    public static String escape(final String text) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index = index + 1) {
            final char item = text.charAt(index);
            if (isAlphanumeric(item)) {
                result.append(item);
            } else {
                result.append('%').append(String.format("%02X", (int) item));
            }
        }
        return result.toString();
    }

    /**
     * Unescapes a keyword back into its original form.
     *
     * @param text The escaped keyword
     * @return The original text
     */
    public static String unescape(final String text) {
        final StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            final char item = text.charAt(index);
            if (item == '%' && index + 2 < text.length()) {
                result.append(
                    (char) Integer.parseInt(
                        text.substring(index + 1, index + 3), 16
                    )
                );
                index = index + 3;
            } else {
                result.append(item);
                index = index + 1;
            }
        }
        return result.toString();
    }

    private static boolean isAlphanumeric(final char item) {
        return Character.isLetterOrDigit(item);
    }
}
