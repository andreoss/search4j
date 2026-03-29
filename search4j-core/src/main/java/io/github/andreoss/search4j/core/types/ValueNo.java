/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Value slot number value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Objects;

/**
 * Number of a value slot in a document.
 *
 * <p>Slot numbers are unsigned 32-bit quantities, carried as a {@code long} in
 * the range 0 to {@link #BAD}. The slot {@link #BAD} means "no value
 * slot".</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.ProhibitPublicStaticMethods", "PMD.AvoidDirectAccessToStaticFields"})
public record ValueNo(long value) implements Comparable<ValueNo> {

    /**
     * Reserved slot number meaning "no value slot".
     */
    public static final long BAD = 0xFFFFFFFFL;

    /**
     * Creates a value slot number.
     *
     * @param value A slot number in the unsigned 32-bit range
     */
    public ValueNo {
        if (value < 0 || value > BAD) {
            throw new IllegalArgumentException(
                String.format("ValueNo is outside the unsigned 32-bit range: %d", value)
            );
        }
    }

    /**
     * Creates a value slot number from its decimal text.
     *
     * @param text Decimal digits of the slot number
     * @return The value slot number
     */
    public static ValueNo parse(final String text) {
        Objects.requireNonNull(text, "ValueNo text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("ValueNo is not decimal digits: %s", text)
            );
        }
        final long number = Long.parseLong(text);
        if (number > BAD) {
            throw new IllegalArgumentException(
                String.format("ValueNo is outside the unsigned 32-bit range: %s", text)
            );
        }
        return new ValueNo(number);
    }

    /**
     * Tests whether this slot number means "no value slot".
     *
     * @return True when equal to {@link #BAD}
     */
    public boolean absent() {
        return this.value == BAD;
    }

    @Override
    public int compareTo(final ValueNo other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
