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
 * <p>Carried as a non-negative {@code long}.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TotalLength(long value) implements Comparable<TotalLength> {

    /**
     * Length of an empty database.
     */
    public static final TotalLength ZERO = new TotalLength(0L);

    /**
     * Creates a total length.
     *
     * @param value A non-negative total length
     */
    public TotalLength {
        if (value < 0L) {
            throw new IllegalArgumentException(
                String.format("TotalLength must not be negative: %d", value)
            );
        }
    }

    /**
     * Creates a total length from its decimal text.
     *
     * @param text Decimal digits of the total length
     * @return The total length
     */
    public static TotalLength parse(final String text) {
        Objects.requireNonNull(text, "TotalLength text");
        if (!text.matches("0|[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException(
                String.format("TotalLength is not decimal digits: %s", text)
            );
        }
        return new TotalLength(Long.parseLong(text));
    }

    /**
     * Adds another total length.
     *
     * @param other The length to add
     * @return The sum
     */
    public TotalLength plus(final TotalLength other) {
        final long sum;
        try {
            sum = Math.addExact(this.value, other.value);
        } catch (final ArithmeticException overflow) {
            throw new IllegalStateException("TotalLength overflow", overflow);
        }
        return new TotalLength(sum);
    }

    @Override
    public int compareTo(final TotalLength other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
