/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * Revision number of a vidrio table, an unsigned 32-bit value.
 *
 * @param value Number from 0 to 4294967295
 * @since 0.1.0
 */
public record RevisionNumber(long value) implements Comparable<RevisionNumber> {

    /**
     * Largest value.
     */
    public static final long MAX = 0xFFFF_FFFFL;

    /**
     * Creates the value.
     *
     * @param value Number from 0 to 4294967295
     */
    public RevisionNumber {
        if (value < 0L || value > RevisionNumber.MAX) {
            throw new IllegalArgumentException(
                String.format("Revision number out of range: %d", value)
            );
        }
    }

    /**
     * Reads the value from its four big-endian bytes.
     *
     * @param bits Raw 32 bits
     * @return Value
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static RevisionNumber fromBits(final int bits) {
        return new RevisionNumber(Integer.toUnsignedLong(bits));
    }

    /**
     * Raw 32 bits for big-endian writing.
     *
     * @return Low 32 bits of the value
     */
    public int bits() {
        return (int) this.value;
    }

    @Override
    public int compareTo(final RevisionNumber other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
