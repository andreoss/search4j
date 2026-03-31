/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * UTF-8 and character classification utilities of the search engine.
 */
package io.github.andreoss.search4j.core.unicode;

/**
 * Text as it appears in a description: printable characters as they are,
 * control characters, the backslash and DEL as a backslash, {@code x} and
 * two hexadecimal digits.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Description {

    /**
     * Utility class, no instances.
     */
    private Description() {
    }

    /**
     * Escapes a text for a description.
     *
     * @param text Text to escape
     * @return Text with every character below the space, the backslash and
     *  DEL replaced by its escape
     */
    public static String escape(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(
            point -> {
                if (point >= ' ' && point != '\\' && point != 0x7F) {
                    out.appendCodePoint(point);
                } else {
                    out.append(String.format("\\x%02x", point & 0xFF));
                }
            }
        );
        return out.toString();
    }
}
