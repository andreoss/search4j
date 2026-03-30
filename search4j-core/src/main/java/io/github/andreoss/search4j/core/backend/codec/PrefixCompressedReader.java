/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Byte encodings of string lists used by the database backends.
 */
package io.github.andreoss.search4j.core.backend.codec;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Decoder of a word list written by {@link PrefixCompressedWriter}.
 *
 * <p>Truncated or inconsistent input raises a database-corrupt error.</p>
 *
 * @since 0.1.0
 */
public final class PrefixCompressedReader implements Iterator<byte[]> {

    /**
     * The encoded input.
     */
    private final byte[] data;

    /**
     * Offset of the next byte to read.
     */
    private int pos;

    /**
     * The word decoded last.
     */
    private byte[] current;

    /**
     * Creates a decoder over a copy of encoded bytes.
     *
     * @param data Encoded word list
     */
    public PrefixCompressedReader(final byte[] data) {
        this.data = data.clone();
        this.pos = 0;
        this.current = new byte[0];
    }

    @Override
    public boolean hasNext() {
        return this.pos < this.data.length;
    }

    @Override
    public byte[] next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("No more words");
        }
        int keep = 0;
        if (this.current.length > 0) {
            keep = Lengths.decode(this.data[this.pos]);
            this.pos += 1;
        }
        final int left = this.data.length - this.pos;
        if (left == 0 || keep > this.current.length
            || Lengths.decode(this.data[this.pos]) >= left) {
            throw new DatabaseCorruptError("Bad prefix-compressed word list");
        }
        final int add = Lengths.decode(this.data[this.pos]);
        final ByteArrayOutputStream word = new ByteArrayOutputStream();
        word.write(this.current, 0, keep);
        word.write(this.data, this.pos + 1, add);
        this.pos += add + 1;
        this.current = word.toByteArray();
        return this.current.clone();
    }
}
