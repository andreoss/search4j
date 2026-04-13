/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term count value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Objects;

/**
 * Count of terms or documents, as in a within-document frequency, a
 * document length, a collection frequency or a document count.
 *
 * <p>Counts are unsigned 32-bit numbers, from 0 to {@code 4294967295},
 * carried in a {@code long}. Document counts share this type although
 * document identifiers bound them below {@code 2^31}.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TermCount(long value) implements Comparable<TermCount> {

    /**
     * Count of zero terms.
     */
    public static final TermCount ZERO = new TermCount(0L);

    /**
     * Count of one term.
     */
    public static final TermCount ONE = new TermCount(1L);

    /**
     * Largest count, the unsigned 32-bit maximum.
     */
    public static final TermCount MAX = new TermCount(0xFFFF_FFFFL);

    /**
     * Creates a term count.
     *
     * @param value A count from 0 to the unsigned 32-bit maximum
     */
    public TermCount {
        if (value < 0L || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException(
                String.format("TermCount out of the 32-bit range: %d", value)
            );
        }
    }

    /**
     * Creates a term count from its decimal text.
     *
     * @param text Decimal digits without leading zeros, at most 4294967295
     * @return Count carrying the parsed number
     */
    public static TermCount parse(final String text) {
        Objects.requireNonNull(text, "TermCount text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("TermCount is not decimal digits: %s", text)
            );
        }
        return new TermCount(Long.parseLong(text));
    }

    /**
     * Creates a term count from the low 32 bits of a number, as unsigned
     * 32-bit arithmetic keeps a sum or difference of counts.
     *
     * @param number Any number, such as a sum or difference of counts
     * @return Count of the number modulo two to the thirty-second
     */
    public static TermCount wrap(final long number) {
        return new TermCount(number & 0xFFFF_FFFFL);
    }

    /**
     * Adds another count in unsigned 32-bit arithmetic, wrapping past the
     * maximum as the reference does.
     *
     * @param other Count to add
     * @return Sum of both counts, modulo two to the thirty-second
     */
    public TermCount plus(final TermCount other) {
        return TermCount.wrap(this.value + other.value);
    }

    /**
     * Subtracts another count in unsigned 32-bit arithmetic, wrapping below
     * zero as the reference does.
     *
     * @param other Count to subtract
     * @return Difference, modulo two to the thirty-second
     */
    public TermCount minus(final TermCount other) {
        return TermCount.wrap(this.value - other.value);
    }

    @Override
    public int compareTo(final TermCount other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
