/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Sortable big-endian encoding of doubles.
 *
 * <p>A double is encoded into eight bytes whose lexicographic order matches
 * the numeric order of the doubles, so encoded doubles can be used directly
 * as B-tree keys.</p>
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
     * Encodes a double into eight sortable bytes.
     *
     * @param value Number to encode
     * @return Eight bytes whose unsigned order matches numeric order
     */
    public static byte[] serialise(final double value) {
        return bigEndian(encode(Double.doubleToLongBits(value)));
    }

    /**
     * Decodes eight sortable bytes back into a double.
     *
     * @param bytes Bytes from {@link #serialise(double)}
     * @return Original number
     */
    public static double deserialise(final byte[] bytes) {
        long bits = 0L;
        for (final byte item : bytes) {
            bits = bits << 8 | item & 0xFFL;
        }
        return Double.longBitsToDouble(decode(bits));
    }

    /**
     * Tests whether one encoding sorts at or before another.
     *
     * @param smaller Number expected to sort first
     * @param larger Number expected to sort last
     * @return True when the first encoding sorts at or before the second
     */
    public static boolean ordered(final double smaller, final double larger) {
        return compareBytes(serialise(smaller), serialise(larger)) <= 0;
    }

    private static long encode(final long bits) {
        final long result;
        if (bits < 0) {
            result = ~bits;
        } else {
            result = bits ^ 0x8000000000000000L;
        }
        return result;
    }

    private static long decode(final long bits) {
        final long result;
        if (bits < 0) {
            result = bits ^ 0x8000000000000000L;
        } else {
            result = ~bits;
        }
        return result;
    }

    private static byte[] bigEndian(final long bits) {
        final byte[] result = new byte[8];
        for (int index = 0; index < 8; index = index + 1) {
            result[index] = (byte) (bits >>> 56 - index * 8);
        }
        return result;
    }

    private static int compareBytes(final byte[] left, final byte[] right) {
        int result = 0;
        final int length = Math.min(left.length, right.length);
        int index = 0;
        while (result == 0 && index < length) {
            result = (left[index] & 0xFF) - (right[index] & 0xFF);
            index = index + 1;
        }
        if (result == 0) {
            result = Integer.compare(left.length, right.length);
        }
        return result;
    }
}
