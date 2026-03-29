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
 * <p>Positions start at 0 and fit a 32-bit signed integer.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record TermPos(int value) implements Comparable<TermPos> {

    /**
     * First term position.
     */
    public static final TermPos FIRST = new TermPos(0);

    /**
     * Creates a term position.
     *
     * @param value A non-negative position
     */
    public TermPos {
        if (value < 0) {
            throw new IllegalArgumentException(
                String.format("TermPos must not be negative: %d", value)
            );
        }
    }

    /**
     * Creates a term position from its decimal text.
     *
     * @param text Decimal digits of the position
     * @return The term position
     */
    public static TermPos parse(final String text) {
        Objects.requireNonNull(text, "TermPos text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("TermPos is not decimal digits: %s", text)
            );
        }
        final long number = Long.parseLong(text);
        if (number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                String.format("TermPos exceeds the 32-bit range: %s", text)
            );
        }
        return new TermPos((int) number);
    }

    /**
     * Next term position.
     *
     * @return The following position
     */
    public TermPos next() {
        if (this.value == Integer.MAX_VALUE) {
            throw new IllegalStateException("TermPos overflow");
        }
        return new TermPos(this.value + 1);
    }

    @Override
    public int compareTo(final TermPos other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(this.value);
    }
}
