/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Code layout of the values below a bound: the width in bits, the number
 * of codes the width leaves spare, and the start of the middle band whose
 * values take one bit less when there are spare codes.
 *
 * @param bits Width of a full code
 * @param spare Codes of the full width not needed
 * @param middle First value of the shorter band
 * @since 0.1.0
 */
record BitRange(int bits, long spare, long middle) {

    /**
     * Largest bound supported, one above the largest 32-bit value.
     */
    private static final long LIMIT = 1L << Integer.SIZE;

    /**
     * Layout of the values below a bound.
     *
     * @param outof Bound from 1 to 2 to the power of 32
     * @return Layout
     */
    static BitRange of(final long outof) {
        if (outof < 1L || outof > BitRange.LIMIT) {
            throw new IllegalArgumentException(
                String.format("Bound out of range: %d", outof)
            );
        }
        final int bits = Long.SIZE - Long.numberOfLeadingZeros(outof - 1L);
        final long spare = (1L << bits) - outof;
        return new BitRange(bits, spare, (outof - spare) / 2L);
    }
}
