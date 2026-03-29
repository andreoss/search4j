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
 * Bit-level writer that accumulates bits most significant first.
 *
 * <p>Values are written in whole-bit chunks and padded to a byte boundary
 * when the stream is finished.</p>
 *
 * @since 0.1.0
 */
public final class BitWriter {

    /**
     * Backing byte buffer.
     */
    private final ByteArrayOutputStream out;

    /**
     * Pending bits not yet flushed.
     */
    private long pending;

    /**
     * Number of valid bits in {@link #pending}.
     */
    private int count;

    /**
     * Creates an empty bit writer.
     */
    public BitWriter() {
        this.out = new ByteArrayOutputStream();
        this.pending = 0L;
        this.count = 0;
    }

    /**
     * Writes the low bits of a value.
     *
     * @param value Value whose higher bits are ignored
     * @param bits Width from 1 to 32; others raise an illegal-argument error
     */
    public void write(final long value, final int bits) {
        if (bits < 1 || bits > 32) {
            throw new IllegalArgumentException(
                String.format("Bits out of range: %d", bits)
            );
        }
        this.pending = this.pending << bits | value & (1L << bits) - 1L;
        this.count = this.count + bits;
        while (this.count >= 8) {
            this.count = this.count - 8;
            this.out.write((int) (this.pending >>> this.count) & 0xFF);
        }
    }

    /**
     * Flushes pending bits, padding with zero bits to a byte boundary.
     *
     * @return Every byte written
     */
    public byte[] finish() {
        if (this.count != 0) {
            this.out.write((int) (this.pending << 8 - this.count) & 0xFF);
            this.count = 0;
        }
        return this.out.toByteArray();
    }
}
