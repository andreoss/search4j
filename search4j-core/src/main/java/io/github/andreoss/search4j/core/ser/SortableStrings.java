/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Byte string packing whose byte order matches the order of the strings.
 *
 * <p>A zero byte is followed by 0xFF, so that a string sorts before its
 * extensions, and a terminating zero byte marks the end unless the string
 * is the last field of its key.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class SortableStrings {

    /**
     * Byte following every zero byte of the string.
     */
    private static final int ESCAPE = 0xFF;

    /**
     * Utility class, no instances.
     */
    private SortableStrings() {
    }

    /**
     * Encodes a string.
     *
     * @param value String bytes
     * @param last True when nothing follows the string in its key
     * @return Encoded bytes
     */
    public static byte[] pack(final byte[] value, final boolean last) {
        final ByteArrayOutputStream out =
            new ByteArrayOutputStream(value.length + 1);
        for (final byte item : value) {
            out.write(item);
            if (item == 0) {
                out.write(SortableStrings.ESCAPE);
            }
        }
        if (!last) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /**
     * Decodes one string at the buffer position and advances past it and
     * its terminator.
     *
     * @param buffer Source positioned at an encoded string
     * @return String bytes
     */
    public static byte[] unpack(final ByteBuffer buffer) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean more = true;
        while (more && buffer.hasRemaining()) {
            final byte item = buffer.get();
            if (item == 0) {
                more = SortableStrings.escaped(buffer);
                if (more) {
                    out.write(0);
                }
            } else {
                out.write(item);
            }
        }
        return out.toByteArray();
    }

    private static boolean escaped(final ByteBuffer buffer) {
        final boolean escaped = buffer.hasRemaining()
            && (buffer.get(buffer.position()) & SortableStrings.ESCAPE)
            == SortableStrings.ESCAPE;
        if (escaped) {
            buffer.get();
        }
        return escaped;
    }
}
