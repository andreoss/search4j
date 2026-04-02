/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Decoder for sortable double values.
 *
 * @since 0.1.0
 */
final class SortableDoubleDecoder {

    private SortableDoubleDecoder() {
    }

    static double decode(final byte[] bytes) {
        final double result;
        if (bytes.length == 1 && bytes[0] == (byte) 0x80) {
            result = 0.0;
        } else if (bytes.length == 9 && SortableDoubleDecoder.allOnes(bytes)) {
            result = Double.POSITIVE_INFINITY;
        } else if (bytes.length == 0) {
            result = Double.NEGATIVE_INFINITY;
        } else {
            result = SortableDoubleDecoder.finite(bytes);
        }
        return result;
    }

    private static double finite(final byte[] bytes) {
        final SortableDoubleParts parts = SortableDoubleDecoder.parts(bytes);
        double mantissa = Integer.toUnsignedLong(parts.low())
            / 4_294_967_296.0;
        mantissa += Integer.toUnsignedLong(parts.high());
        int shift = 27;
        if (parts.negative()) {
            shift = 26;
            mantissa = -mantissa;
        }
        mantissa /= 1 << shift;
        return Math.scalb(mantissa, parts.exponent());
    }

    private static SortableDoubleParts parts(final byte[] bytes) {
        int index = 0;
        int first = SortableDoubleDecoder.byteAt(bytes, index);
        first ^= (first & 0xC0) >>> 1;
        final boolean negative = (first & 0x80) == 0;
        final boolean expneg = (first & 0x40) != 0;
        final boolean longexp = (first & 0x20) == 0;
        int exponent = first & 0x1F;
        if (longexp) {
            index += 1;
            first = SortableDoubleDecoder.byteAt(bytes, index);
            exponent = exponent << 6 | first >> 2;
        } else {
            exponent >>= 2;
        }
        if (negative ^ expneg) {
            exponent = SortableDoubleDecoder.unflip(exponent, longexp);
        }
        int high = (first & 0x03) << 24;
        index += 1;
        high |= SortableDoubleDecoder.byteAt(bytes, index) << 16;
        index += 1;
        high |= SortableDoubleDecoder.byteAt(bytes, index) << 8;
        index += 1;
        high |= SortableDoubleDecoder.byteAt(bytes, index);
        int low = 0;
        if (index < bytes.length) {
            low = SortableDoubleDecoder.wordAt(bytes, index + 1);
        }
        if (negative) {
            high = -high;
            if (low != 0) {
                high += 1;
            }
            low = -low;
            high &= 0x03FFFFFF;
        } else {
            high |= 1 << 26;
        }
        if (expneg) {
            exponent = -exponent;
        }
        return new SortableDoubleParts(
            negative, exponent + 8, high, low
        );
    }

    private static int unflip(final int exponent, final boolean longexp) {
        final int mask;
        if (longexp) {
            mask = 0x07FF;
        } else {
            mask = 0x07;
        }
        return exponent ^ mask;
    }

    private static int wordAt(final byte[] bytes, final int offset) {
        return SortableDoubleDecoder.byteAt(bytes, offset) << 24
            | SortableDoubleDecoder.byteAt(bytes, offset + 1) << 16
            | SortableDoubleDecoder.byteAt(bytes, offset + 2) << 8
            | SortableDoubleDecoder.byteAt(bytes, offset + 3);
    }

    private static int byteAt(final byte[] bytes, final int index) {
        final int result;
        if (index < bytes.length) {
            result = bytes[index] & 0xFF;
        } else {
            result = 0;
        }
        return result;
    }

    private static boolean allOnes(final byte[] bytes) {
        boolean result = true;
        for (final byte value : bytes) {
            if (value != (byte) 0xFF) {
                result = false;
            }
        }
        return result;
    }
}
