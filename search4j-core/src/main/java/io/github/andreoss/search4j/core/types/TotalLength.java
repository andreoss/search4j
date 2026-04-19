/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Total document length value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Objects;

/**
 * Total length of all documents in a database.
 *
 * <p>Totals are unsigned 64-bit numbers, from 0 to
 * {@code 18446744073709551615}, carried in the bits of a {@code long}:
 * a negative {@code long} stands for a total of two to the sixty-third or
 * more.</p>
 *
 * @param value The underlying bits
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TotalLength(long value) implements Comparable<TotalLength> {

    /**
     * Length of an empty database.
     */
    public static final TotalLength ZERO = new TotalLength(0L);

    /**
     * Creates a total length from its decimal text.
     *
     * @param text Decimal digits without leading zeros, at most
     *  18446744073709551615
     * @return Length carrying the parsed number
     */
    public static TotalLength parse(final String text) {
        Objects.requireNonNull(text, "TotalLength text");
        if (!text.matches("0|[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException(
                String.format("TotalLength is not decimal digits: %s", text)
            );
        }
        return new TotalLength(Long.parseUnsignedLong(text));
    }

    /**
     * Adds another total length in unsigned 64-bit arithmetic, wrapping past
     * the maximum as the reference does.
     *
     * @param other Length to add
     * @return Sum of both lengths, modulo two to the sixty-fourth
     */
    public TotalLength plus(final TotalLength other) {
        return new TotalLength(this.value + other.value);
    }

    /**
     * Nearest double of the unsigned total, ties to even.
     *
     * @return Total as a double
     */
    public double asDouble() {
        return TotalLength.unsigned(this.value);
    }

    /**
     * Nearest double of the bits of an unsigned 64-bit number, ties to even,
     * as the reference converts an unsigned 64-bit value.
     *
     * @param bits Bits of the unsigned number
     * @return Number as a double
     */
    public static double unsigned(final long bits) {
        final double result;
        if (bits >= 0L) {
            result = bits;
        } else {
            result = Math.scalb((double) (bits >>> 1 | bits & 1L), 1);
        }
        return result;
    }

    /**
     * Total as a count, saturated at the unsigned 32-bit maximum.
     *
     * @return The total, or {@link TermCount#MAX} when it is larger
     */
    public TermCount clamped() {
        final TermCount result;
        if (Long.compareUnsigned(this.value, TermCount.MAX.value()) > 0) {
            result = TermCount.MAX;
        } else {
            result = new TermCount(this.value);
        }
        return result;
    }

    @Override
    public int compareTo(final TotalLength other) {
        return Long.compareUnsigned(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toUnsignedString(this.value);
    }
}
