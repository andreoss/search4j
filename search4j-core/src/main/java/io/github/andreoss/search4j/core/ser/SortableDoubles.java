/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Variable-length sortable encoding of doubles.
 *
 * <p>Encoded values sort lexicographically in numeric order.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class SortableDoubles {

    /**
     * Utility class, no instances.
     */
    private SortableDoubles() {
    }

    /**
     * Encodes a double into sortable bytes.
     *
     * @param value Number to encode
     * @return Variable-length bytes in numeric order
     */
    public static byte[] serialise(final double value) {
        return SortableDoubleEncoder.encode(value);
    }

    /**
     * Decodes sortable bytes into a double.
     *
     * @param bytes Bytes from {@link #serialise(double)}
     * @return Decoded number
     */
    public static double deserialise(final byte[] bytes) {
        return SortableDoubleDecoder.decode(bytes);
    }

    /**
     * Tests whether one encoding sorts at or before another.
     *
     * @param smaller Number expected to sort first
     * @param larger Number expected to sort last
     * @return True when the first encoding sorts at or before the second
     */
    public static boolean ordered(final double smaller, final double larger) {
        return SortableDoubleOrder.ordered(smaller, larger);
    }
}
