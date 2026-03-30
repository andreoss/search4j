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
 * Encoder of a word list where each word shares a prefix with the one
 * before.
 *
 * <p>The first word is its length byte then its bytes; each later word is the
 * length of the prefix kept from the previous word, the length of the bytes
 * added, then those bytes. Each length is one masked byte.</p>
 *
 * @since 0.1.0
 */
public final class PrefixCompressedWriter {

    /**
     * The encoded output.
     */
    private final ByteArrayOutputStream out;

    /**
     * The word written last.
     */
    private byte[] current;

    /**
     * Creates an empty encoder.
     */
    public PrefixCompressedWriter() {
        this.out = new ByteArrayOutputStream();
        this.current = new byte[0];
    }

    /**
     * Appends a word.
     *
     * @param word Word of at most 255 bytes
     */
    public void append(final byte[] word) {
        if (this.current.length == 0) {
            this.out.write(Lengths.encode(word.length));
            this.out.write(word, 0, word.length);
        } else {
            final int keep = this.shared(word);
            final int add = word.length - keep;
            this.out.write(Lengths.encode(keep));
            this.out.write(Lengths.encode(add));
            this.out.write(word, keep, add);
        }
        this.current = word.clone();
    }

    /**
     * Encoded form of the words appended so far.
     *
     * @return Encoded bytes
     */
    public byte[] bytes() {
        return this.out.toByteArray();
    }

    private int shared(final byte[] word) {
        final int limit = Math.min(word.length, this.current.length);
        int index = 0;
        while (index < limit && word[index] == this.current[index]) {
            index += 1;
        }
        return index;
    }
}
