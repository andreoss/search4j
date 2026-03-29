/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Bit-level reader that consumes bits most significant first.
 *
 * @since 0.1.0
 */
public final class BitReader {

    /**
     * The source bytes.
     */
    private final byte[] data;

    /**
     * The next bit index to read.
     */
    private int bit;

    /**
     * Creates a reader over the given bytes.
     *
     * @param data The source bytes
     */
    public BitReader(final byte[] data) {
        this.data = data.clone();
    }

    /**
     * Reads a value using the given number of bits.
     *
     * @param bits The number of bits, at least one
     * @return The value read
     */
    public long read(final int bits) {
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException(
                String.format("Bits out of range: %d", bits)
            );
        }
        long value = 0L;
        for (int remaining = bits; remaining > 0; remaining = remaining - 1) {
            value = value << 1 | this.nextBit();
        }
        return value;
    }

    private long nextBit() {
        final int index = this.bit / 8;
        final int offset = 7 - this.bit % 8;
        this.bit = this.bit + 1;
        final long result;
        if (index >= this.data.length) {
            result = 0L;
        } else {
            result = this.data[index] >>> offset & 1L;
        }
        return result;
    }
}
