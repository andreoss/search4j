/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Variable-length unsigned integer packing.
 *
 * <p>Each value is written as seven-bit groups, least significant first, with
 * the high bit marking a continuation byte.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Varints {

    /**
     * Utility class, no instances.
     */
    private Varints() {
    }

    /**
     * Encodes an unsigned integer into its variable-length form.
     *
     * @param value Non-negative value; a negative one raises an error
     * @return One to nine bytes
     */
    public static byte[] pack(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException(
                String.format("Negative value: %d", value)
            );
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = value;
        while (remaining >= 0x80) {
            out.write((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write((int) remaining);
        return out.toByteArray();
    }

    /**
     * Encodes the bits of an unsigned 64-bit integer into its variable-length
     * form; a negative {@code long} stands for two to the sixty-third or more.
     *
     * @param bits Bits of the unsigned value
     * @return One to ten bytes
     */
    public static byte[] packUnsigned(final long bits) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long remaining = bits;
        while (Long.compareUnsigned(remaining, 0x80L) >= 0) {
            out.write((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write((int) remaining);
        return out.toByteArray();
    }

    /**
     * Decodes a variable-length integer from its encoded form.
     *
     * @param bytes Bytes from {@link #pack(long)}
     * @return Original value
     */
    public static long unpack(final byte[] bytes) {
        long result = 0L;
        int shift = 0;
        for (final byte item : bytes) {
            result |= (item & 0x7FL) << shift;
            shift += 7;
        }
        return result;
    }

    /**
     * Encodes an unsigned integer to an output stream.
     *
     * @param out Output stream
     * @param value Non-negative value
     * @throws IOException when writing fails
     */
    public static void encode(final OutputStream out, final long value)
        throws IOException {
        long remaining = value;
        while (remaining >= 0x80) {
            out.write((int) (remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write((int) remaining);
    }

    /**
     * Decodes a variable-length integer from an input stream.
     *
     * @param source Input stream
     * @return Original value
     * @throws IOException when reading fails
     */
    public static int decode(final InputStream source) throws IOException {
        long result = 0L;
        int shift = 0;
        int read = source.read();
        while (read >= 0) {
            result |= (read & 0x7FL) << shift;
            if ((read & 0x80) == 0) {
                return (int) result;
            }
            shift += 7;
            read = source.read();
        }
        throw new IOException("Unexpected end of stream");
    }
}
