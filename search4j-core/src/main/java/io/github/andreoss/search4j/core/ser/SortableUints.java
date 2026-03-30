/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.nio.ByteBuffer;

/**
 * Unsigned integer packing whose byte order matches numeric order.
 *
 * <p>Values below 32768 take two bytes with a clear top bit; larger values
 * take a length prefix of leading one bits followed by big-endian bytes, up
 * to nine bytes for a full 64-bit value. The first byte is never 0xFF.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class SortableUints {

    /**
     * Utility class, no instances.
     */
    private SortableUints() {
    }

    /**
     * Encodes an unsigned integer.
     *
     * @param value Value read as unsigned 64 bits
     * @return Two to nine bytes
     */
    public static byte[] pack(final long value) {
        final byte[] out;
        if (Long.compareUnsigned(value, 0x8000L) < 0) {
            out = new byte[] {(byte) (value >>> 8), (byte) value};
        } else {
            final int len = (69 - Long.numberOfLeadingZeros(value)) / 7;
            out = new byte[len];
            long rest = value;
            for (int idx = len - 1; idx > 0; --idx) {
                out[idx] = (byte) rest;
                rest >>>= 8;
            }
            out[0] = (byte) (rest | 0xFFL << 10 - len);
        }
        return out;
    }

    /**
     * Decodes one unsigned integer at the buffer position and advances it.
     *
     * @param buffer Source positioned at an encoded value
     * @return Value as unsigned 64 bits
     */
    public static long unpack(final ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new SerialisationError("No sortable integer to decode");
        }
        final byte lead = buffer.get();
        final int width = SortableUints.width(lead);
        if (width == 0) {
            throw new SerialisationError("Bad sortable integer lead byte");
        }
        if (buffer.remaining() < width - 1) {
            throw new SerialisationError("Truncated sortable integer");
        }
        long result = lead & 0xFF >>> width - 1;
        for (int idx = 1; idx < width; ++idx) {
            result = result << 8 | buffer.get() & 0xFFL;
        }
        return result;
    }

    /**
     * Encoded width of the value that starts with a lead byte.
     *
     * @param lead First byte of an encoded value
     * @return Width from 2 to 9 bytes, or 0 for the invalid lead 0xFF
     */
    public static int width(final byte lead) {
        final int bits = lead & 0xFF;
        final int width;
        if (bits < 0x80) {
            width = 2;
        } else if (bits == 0xFF) {
            width = 0;
        } else {
            width = Integer.numberOfLeadingZeros(bits ^ 0xFF) - 22;
        }
        return width;
    }
}
