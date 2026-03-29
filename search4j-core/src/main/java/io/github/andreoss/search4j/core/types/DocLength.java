/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Normalised document length value type.
 */
package io.github.andreoss.search4j.core.types;

/**
 * Normalised document length: the document length divided by the average
 * document length in the database.
 *
 * <p>Carried as a finite, non-negative {@code double}.</p>
 *
 * @param value The underlying number
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record DocLength(double value) implements Comparable<DocLength> {

    /**
     * Length of a document with no terms.
     */
    public static final DocLength ZERO = new DocLength(0.0);

    /**
     * Creates a normalised document length.
     *
     * @param value A finite, non-negative length
     */
    public DocLength {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                String.format("DocLength must be finite and non-negative: %s", value)
            );
        }
    }

    /**
     * Creates a normalised document length from its decimal text.
     *
     * @param text Decimal text of the length
     * @return The normalised document length
     */
    public static DocLength parse(final String text) {
        return new DocLength(Double.parseDouble(text));
    }

    @Override
    public int compareTo(final DocLength other) {
        return Double.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Double.toString(this.value);
    }
}
