/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;

/**
 * Bit-level writer of values below a known bound, least significant bit
 * first, with interpolative coding of ascending sequences.
 *
 * <p>A value below a bound takes as many bits as the bound needs; when
 * the width leaves codes spare, the values of the middle band take one
 * bit less. The stream is padded with zero bits to a byte boundary when
 * frozen.</p>
 *
 * @since 0.1.0
 */
public final class BitWriter {

    /**
     * Bytes written.
     */
    private final ByteArrayOutputStream out;

    /**
     * Pending bits not yet written.
     */
    private long pending;

    /**
     * Number of valid bits in {@link #pending}.
     */
    private int count;

    /**
     * Creates an empty writer.
     */
    public BitWriter() {
        this.out = new ByteArrayOutputStream();
        this.pending = 0L;
        this.count = 0;
    }

    /**
     * Encodes a value known to be below a bound.
     *
     * @param value Value from 0 below the bound
     * @param outof Bound from 1 to 2 to the power of 32
     */
    public void encode(final long value, final long outof) {
        if (value < 0L || value >= outof) {
            throw new IllegalArgumentException(
                String.format("Value %d is not below %d", value, outof)
            );
        }
        final BitRange range = BitRange.of(outof);
        int bits = range.bits();
        long code = value;
        if (range.spare() != 0L) {
            final long upper = range.middle() + range.spare();
            if (value >= upper) {
                code = value - upper | 1L << bits - 1;
            } else if (value >= range.middle()) {
                bits = bits - 1;
            }
        }
        this.pending |= code << this.count;
        this.count = this.count + bits;
        while (this.count >= Byte.SIZE) {
            this.out.write((int) (this.pending & 0xFFL));
            this.pending >>>= Byte.SIZE;
            this.count = this.count - Byte.SIZE;
        }
    }

    /**
     * Encodes the values strictly between two indexes of an ascending
     * sequence, given that the values at the indexes are known.
     *
     * @param values Strictly ascending values
     * @param low Index of the first value known
     * @param high Index of the last value known
     */
    public void encodeInterpolative(
        final long[] values, final int low, final int high
    ) {
        int start = low;
        while (start + 1 < high) {
            final int mid = start + (high - start) / 2;
            final long outof = values[high] - values[start] + start - high + 1L;
            final long lowest = values[start] + mid - start;
            this.encode(values[mid] - lowest, outof);
            this.encodeInterpolative(values, start, mid);
            start = mid;
        }
    }

    /**
     * Pads the pending bits with zero bits to a byte boundary and returns
     * every byte written.
     *
     * @return Bytes of the stream
     */
    public byte[] freeze() {
        if (this.count != 0) {
            this.out.write((int) (this.pending & 0xFFL));
            this.pending = 0L;
            this.count = 0;
        }
        return this.out.toByteArray();
    }
}
