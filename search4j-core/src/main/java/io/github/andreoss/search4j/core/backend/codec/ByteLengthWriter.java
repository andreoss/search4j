/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Byte encodings of string lists used by the database backends.
 */
package io.github.andreoss.search4j.core.backend.codec;

import java.io.ByteArrayOutputStream;

/**
 * Encoder of a string list where each string is its masked length byte then
 * its bytes.
 *
 * @since 0.1.0
 */
public final class ByteLengthWriter {

    /**
     * The encoded output.
     */
    private final ByteArrayOutputStream out;

    /**
     * Creates an empty encoder.
     */
    public ByteLengthWriter() {
        this.out = new ByteArrayOutputStream();
    }

    /**
     * Appends a string.
     *
     * @param item String of at most 255 bytes
     */
    public void append(final byte[] item) {
        this.out.write(Lengths.encode(item.length));
        this.out.write(item, 0, item.length);
    }

    /**
     * Encoded form of the strings appended so far.
     *
     * @return Encoded bytes
     */
    public byte[] bytes() {
        return this.out.toByteArray();
    }
}
