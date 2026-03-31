/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Read position in a serialised query, unpacking integers, strings and
 * doubles the way the reference does.
 *
 * @since 0.1.0
 */
final class Cursor {

    /**
     * Reference message of a packed value cut short.
     */
    static final String SHORT = "Insufficient serialised data";

    /**
     * Reference message of a packed integer too large for its type.
     */
    static final String OVERFLOW = "Serialised data overflowed type";

    /**
     * Message of a value the Java types cannot hold.
     */
    static final String UNSUPPORTED = "Unsupported serialised Query";

    /**
     * The serialised query.
     */
    private final byte[] data;

    /**
     * Index of the next byte.
     */
    private int index;

    /**
     * Creates the cursor at the start of the data.
     *
     * @param data The serialised query
     */
    Cursor(final byte[] data) {
        this.data = data.clone();
    }

    /**
     * Tests whether every byte has been read.
     *
     * @return True at the end of the data
     */
    boolean exhausted() {
        return this.index == this.data.length;
    }

    /**
     * Number of bytes left.
     *
     * @return Bytes not read yet
     */
    int remaining() {
        return this.data.length - this.index;
    }

    /**
     * Reads one byte.
     *
     * @return The byte as an unsigned value
     */
    int next() {
        final int position = this.index;
        this.index = position + 1;
        return this.data[position] & 0xFF;
    }

    /**
     * Unpacks an unsigned integer with the reference messages.
     *
     * @param bits Width of the reference type, 32 or 64
     * @return The value
     */
    long uint(final int bits) {
        return this.uint(bits, Cursor.SHORT, Cursor.OVERFLOW);
    }

    /**
     * Unpacks an unsigned integer: seven bits per byte, least significant
     * first, the high bit marking a continuation.
     *
     * @param bits Width of the reference type, 32 or 64
     * @param shortage Message when the data ends inside the integer
     * @param overflow Message when the integer does not fit the type
     * @return The value; a 64-bit value above the signed range raises an
     *  unsupported-data error
     */
    long uint(final int bits, final String shortage, final String overflow) {
        final int start = this.index;
        int end = start;
        do {
            if (end == this.data.length) {
                throw new SerialisationError(shortage);
            }
            ++end;
        } while ((this.data[end - 1] & 0x80) != 0);
        this.index = end;
        final int count = end - start;
        if (7 * count - 6 > bits) {
            throw new SerialisationError(overflow);
        }
        long value = 0L;
        for (int item = Math.min(count, 9) - 1; item >= 0; --item) {
            value = value << 7 | this.data[start + item] & 0x7F;
        }
        if (count == 10) {
            final int top = this.data[start + 9];
            if (top > 1) {
                throw new SerialisationError(overflow);
            }
            if (top == 1) {
                throw new SerialisationError(Cursor.UNSUPPORTED);
            }
        }
        if (bits == 32 && value > 0xFFFF_FFFFL) {
            throw new SerialisationError(overflow);
        }
        return value;
    }

    /**
     * Reads UTF-8 text of a known length.
     *
     * @param length Number of bytes, not above {@link #remaining()}
     * @return The text; invalid UTF-8 raises an unsupported-data error
     */
    String text(final int length) {
        final CharBuffer chars = CharBuffer.allocate(length);
        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        if (
            decoder.decode(ByteBuffer.wrap(this.data, this.index, length), chars, true)
                .isError()
                || decoder.flush(chars).isError()
        ) {
            throw new SerialisationError(Cursor.UNSUPPORTED);
        }
        this.index += length;
        return chars.flip().toString();
    }

    /**
     * Unpacks a length-prefixed string with the reference messages.
     *
     * @return The string
     */
    String string() {
        return this.string(Cursor.SHORT, Cursor.OVERFLOW);
    }

    /**
     * Unpacks a length-prefixed string.
     *
     * @param shortage Message when the data ends inside the string
     * @param overflow Message when the length does not fit its type
     * @return The string
     */
    String string(final String shortage, final String overflow) {
        final long length = this.uint(64, shortage, overflow);
        if (length > this.remaining()) {
            throw new SerialisationError(shortage);
        }
        return this.text((int) length);
    }

    /**
     * Reads a double stored as its eight little-endian IEEE 754 bytes.
     *
     * @return The double
     */
    double real() {
        if (this.remaining() < 8) {
            throw new SerialisationError("Bad encoded double: insufficient data");
        }
        final double value = ByteBuffer.wrap(this.data, this.index, 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getDouble();
        this.index += 8;
        return value;
    }
}
