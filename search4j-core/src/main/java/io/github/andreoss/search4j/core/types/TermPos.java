/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term position value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Objects;

/**
 * Position of a term within a document or a query.
 *
 * <p>Positions are unsigned 32-bit numbers, from 0 to {@code 4294967295},
 * carried in a {@code long}.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TermPos(long value) implements Comparable<TermPos> {

    /**
     * First term position.
     */
    public static final TermPos FIRST = new TermPos(0L);

    /**
     * Largest term position, the unsigned 32-bit maximum.
     */
    public static final TermPos MAX = new TermPos(0xFFFF_FFFFL);

    /**
     * Creates a term position.
     *
     * @param value A position from 0 to the unsigned 32-bit maximum
     */
    public TermPos {
        if (value < 0L || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException(
                String.format("TermPos out of the 32-bit range: %d", value)
            );
        }
    }

    /**
     * Creates a term position from its decimal text.
     *
     * @param text Decimal digits without leading zeros, at most 4294967295
     * @return Position carrying the parsed number
     */
    public static TermPos parse(final String text) {
        Objects.requireNonNull(text, "TermPos text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("TermPos is not decimal digits: %s", text)
            );
        }
        return new TermPos(Long.parseLong(text));
    }

    /**
     * Creates a term position from the low 32 bits of a number, as unsigned
     * 32-bit arithmetic keeps a sum or difference of positions.
     *
     * @param number Any number, such as a sum or difference of positions
     * @return Position of the number modulo two to the thirty-second
     */
    public static TermPos wrap(final long number) {
        return new TermPos(number & 0xFFFF_FFFFL);
    }

    /**
     * Position one above this one, wrapping past the maximum as unsigned
     * 32-bit arithmetic does.
     *
     * @return Successor, {@link #FIRST} after the maximum
     */
    public TermPos next() {
        return TermPos.wrap(this.value + 1L);
    }

    @Override
    public int compareTo(final TermPos other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
