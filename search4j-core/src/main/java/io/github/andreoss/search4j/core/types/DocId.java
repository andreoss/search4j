/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Document identifier value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.Objects;

/**
 * Unique identifier of a document.
 *
 * <p>Identifier 0 means "no valid document", so valid identifiers are greater
 * than zero. The valid range is {@code 1} to {@link Integer#MAX_VALUE}.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record DocId(int value) implements Comparable<DocId> {

    /**
     * Identifier meaning "no valid document".
     */
    public static final DocId NONE = new DocId(0);

    /**
     * Creates a document identifier.
     *
     * @param value A non-negative document number
     */
    public DocId {
        if (value < 0) {
            throw new IllegalArgumentException(
                String.format("DocId must not be negative: %d", value)
            );
        }
    }

    /**
     * Creates a document identifier from a positive number.
     *
     * @param number A positive document number
     * @return The document identifier
     */
    public static DocId of(final int number) {
        if (number <= 0) {
            throw new IllegalArgumentException(
                String.format("DocId must be positive: %d", number)
            );
        }
        return new DocId(number);
    }

    /**
     * Creates a document identifier from its decimal text.
     *
     * @param text Decimal digits of the document number
     * @return The document identifier
     */
    public static DocId parse(final String text) {
        Objects.requireNonNull(text, "DocId text");
        if (!text.matches("0|[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException(
                String.format("DocId is not decimal digits: %s", text)
            );
        }
        final long number = Long.parseLong(text);
        if (number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                String.format("DocId exceeds the 32-bit range: %s", text)
            );
        }
        return new DocId((int) number);
    }

    /**
     * Tests whether this identifier refers to a valid document.
     *
     * @return True when greater than zero
     */
    public boolean valid() {
        return this.value > 0;
    }

    /**
     * Next document identifier.
     *
     * @return The following identifier
     */
    public DocId next() {
        if (this.value == Integer.MAX_VALUE) {
            throw new IllegalStateException("DocId overflow");
        }
        return new DocId(this.value + 1);
    }

    @Override
    public int compareTo(final DocId other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Integer.toString(this.value);
    }
}
