/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * UTF-8 and character classification utilities of the search engine.
 */
package io.github.andreoss.search4j.core.unicode;

import java.nio.charset.StandardCharsets;

/**
 * Decoding of UTF-8 code points from a byte sequence.
 *
 * <p>Yields one code point per iteration, rejecting malformed sequences.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Utf8 {

    /**
     * Utility class, no instances.
     */
    private Utf8() {
    }

    /**
     * Decodes a UTF-8 byte sequence into its code points.
     *
     * @param bytes The UTF-8 bytes
     * @return The code points in order
     */
    public static int[] codepoints(final byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).codePoints().toArray();
    }

    /**
     * Returns the number of bytes in the lead byte of a code point.
     *
     * @param lead The leading byte
     * @return The sequence length, between one and four
     */
    public static int length(final byte lead) {
        final int value = lead & 0xFF;
        final int result;
        if (value < 0x80) {
            result = 1;
        } else if (value < 0xE0) {
            result = 2;
        } else if (value < 0xF0) {
            result = 3;
        } else {
            result = 4;
        }
        return result;
    }
}
