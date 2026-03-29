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
 * Count of terms, as in a within-document frequency or a database frequency.
 *
 * <p>Counts are non-negative and fit a 32-bit signed integer.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TermCount(int value) implements Comparable<TermCount> {

    /**
     * Count of zero terms.
     */
    public static final TermCount ZERO = new TermCount(0);

    /**
     * Creates a term count.
     *
     * @param value A non-negative count
     */
    public TermCount {
        if (value < 0) {
            throw new IllegalArgumentException(
                String.format("TermCount must not be negative: %d", value)
            );
        }
    }

    /**
     * Creates a term count from its decimal text.
     *
     * @param text Decimal digits without leading zeros, at most 2147483647
     * @return Count carrying the parsed number
     */
    public static TermCount parse(final String text) {
        Objects.requireNonNull(text, "TermCount text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("TermCount is not decimal digits: %s", text)
            );
        }
        final long number = Long.parseLong(text);
        if (number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                String.format("TermCount exceeds the 32-bit range: %s", text)
            );
        }
        return new TermCount((int) number);
    }

    /**
     * Adds another count.
     *
     * @param other Count to add
     * @return Sum of both counts
     */
    public TermCount plus(final TermCount other) {
        final long sum = (long) this.value + other.value;
        if (sum > Integer.MAX_VALUE) {
            throw new IllegalStateException("TermCount overflow");
        }
        return new TermCount((int) sum);
    }

    @Override
    public int compareTo(final TermCount other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(this.value);
    }
}
