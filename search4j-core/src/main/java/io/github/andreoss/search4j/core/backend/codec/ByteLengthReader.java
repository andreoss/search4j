/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Byte encodings of string lists used by the database backends.
 */
package io.github.andreoss.search4j.core.backend.codec;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Decoder of a string list written by {@link ByteLengthWriter}.
 *
 * <p>Truncated input raises a database-corrupt error.</p>
 *
 * @since 0.1.0
 */
public final class ByteLengthReader implements Iterator<byte[]> {

    /**
     * The encoded input.
     */
    private final byte[] data;

    /**
     * Offset of the next length byte.
     */
    private int pos;

    /**
     * Creates a decoder over a copy of encoded bytes.
     *
     * @param data Encoded string list
     */
    public ByteLengthReader(final byte[] data) {
        this.data = data.clone();
        this.pos = 0;
    }

    @Override
    public boolean hasNext() {
        return this.pos < this.data.length;
    }

    @Override
    public byte[] next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("No more strings");
        }
        final int length = Lengths.decode(this.data[this.pos]);
        final int start = this.pos + 1;
        if (start + length > this.data.length) {
            throw new DatabaseCorruptError("Bad byte-length string list");
        }
        this.pos = start + length;
        return Arrays.copyOfRange(this.data, start, this.pos);
    }
}
