/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bit-level reader of a stream written by {@link BitWriter}, with
 * on-demand interpolative decoding of an ascending sequence.
 *
 * <p>Reading past the end of the stream raises a serialisation error.
 * A reader decodes one interpolative sequence.</p>
 *
 * @since 0.1.0
 */
public final class BitReader {

    /**
     * The source bytes.
     */
    private final byte[] data;

    /**
     * Ranges left to decode, innermost first.
     */
    private final Deque<Frame> stack;

    /**
     * Offset of the next byte.
     */
    private int pos;

    /**
     * Bits read from the bytes and not consumed yet.
     */
    private long acc;

    /**
     * Number of valid bits in {@link #acc}.
     */
    private int count;

    /**
     * Index of the lower bound of the range being decoded.
     */
    private int low;

    /**
     * Index of the upper bound of the range being decoded, below the
     * lower index before a sequence is started.
     */
    private int high;

    /**
     * Value at the lower index.
     */
    private long lower;

    /**
     * Value at the upper index.
     */
    private long upper;

    /**
     * Creates a reader over a copy of the bytes.
     *
     * @param data Bytes to read
     */
    public BitReader(final byte[] data) {
        this.data = data.clone();
        this.stack = new ArrayDeque<>();
        this.pos = 0;
        this.acc = 0L;
        this.count = 0;
        this.low = 1;
        this.high = 0;
        this.lower = 0L;
        this.upper = 0L;
    }

    /**
     * Decodes a value known to be below a bound.
     *
     * @param outof Bound from 1 to 2 to the power of 32
     * @return Value below the bound
     */
    public long decode(final long outof) {
        final BitRange range = BitRange.of(outof);
        long value;
        if (range.spare() == 0L) {
            value = this.read(range.bits());
        } else {
            value = this.read(range.bits() - 1);
            if (value < range.middle() && this.read(1) != 0L) {
                value = value + range.middle() + range.spare();
            }
        }
        return value;
    }

    /**
     * Tests whether the stream is consumed: no byte left and at most the
     * padding of zero bits.
     *
     * @return True when nothing but padding remains
     */
    public boolean allGone() {
        return this.pos == this.data.length && this.count < Byte.SIZE
            && this.acc == 0L;
    }

    /**
     * Starts decoding the values strictly between index 0 and a last
     * index of an ascending sequence, given the values at both indexes.
     *
     * @param last Index of the last value known
     * @param start Value at index 0
     * @param end Value at the last index
     */
    public void decodeInterpolative(
        final int last, final long start, final long end
    ) {
        if (this.low <= this.high) {
            throw new IllegalStateException("Sequence already started");
        }
        this.low = 0;
        this.high = last;
        this.lower = start;
        this.upper = end;
    }

    /**
     * Decodes the next value of the sequence, the value at the last index
     * once the sequence is exhausted.
     *
     * @return Next value in ascending order
     */
    public long decodeInterpolativeNext() {
        if (this.low > this.high) {
            throw new IllegalStateException("Sequence not started");
        }
        long result = this.upper;
        if (!this.stack.isEmpty() || this.hasMiddle()) {
            while (this.hasMiddle()) {
                this.descend();
            }
            result = this.ascend();
        }
        return result;
    }

    private boolean hasMiddle() {
        return this.low + 1 < this.high;
    }

    private void descend() {
        this.stack.push(new Frame(this.low, this.high, this.upper));
        final int mid = (this.low + this.high) / 2;
        this.upper = this.decode(
            this.upper - this.lower - (this.high - this.low) + 1L
        ) + this.lower + mid - this.low;
        this.high = mid;
    }

    private long ascend() {
        final long result = this.upper;
        final Frame frame = this.stack.pop();
        this.low = (frame.low() + frame.high()) / 2;
        this.lower = result;
        this.high = frame.high();
        this.upper = frame.upper();
        return result;
    }

    private long read(final int bits) {
        while (this.count < bits) {
            if (this.pos == this.data.length) {
                throw new SerialisationError("Bit stream ran out");
            }
            this.acc |= (this.data[this.pos] & 0xFFL) << this.count;
            this.pos = this.pos + 1;
            this.count = this.count + Byte.SIZE;
        }
        final long held = this.acc;
        this.acc = held >>> bits;
        this.count = this.count - bits;
        return held & (1L << bits) - 1L;
    }

    /**
     * A range whose decoding is suspended while its lower half is
     * decoded.
     *
     * @param low Index of the lower bound
     * @param high Index of the upper bound
     * @param upper Value at the upper index
     * @since 0.1.0
     */
    private record Frame(int low, int high, long upper) {
    }
}
