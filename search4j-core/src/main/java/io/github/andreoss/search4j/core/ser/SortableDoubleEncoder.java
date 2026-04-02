/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Encoder for sortable double values.
 *
 * @since 0.1.0
 */
final class SortableDoubleEncoder {

    private SortableDoubleEncoder() {
    }

    @SuppressWarnings("PMD.ConfusingTernary")
    static byte[] encode(final double value) {
        final byte[] result;
        if (!Double.isFinite(value)) {
            if (value < 0.0) {
                result = new byte[0];
            } else {
                result = new byte[9];
                Arrays.fill(result, (byte) 0xFF);
            }
        } else if (value == 0.0) {
            result = new byte[] {(byte) 0x80};
        } else {
            result = SortableDoubleEncoder.finite(value);
        }
        return result;
    }

    private static byte[] finite(final double value) {
        final boolean negative = value < 0.0;
        final double absolute = Math.abs(value);
        final int binary = SortableDoubleEncoder.binaryExponent(absolute);
        final double mantissa = SortableDoubleEncoder.mantissa(absolute, binary);
        int exponent = binary - 8;
        final boolean expneg = exponent < 0;
        if (expneg) {
            exponent = -exponent;
        }
        final ByteArrayOutputStream output = new ByteArrayOutputStream(9);
        final int next = SortableDoubleEncoder.header(
            exponent, negative, expneg, output
        );
        final int[] words = SortableDoubleEncoder.words(mantissa, negative);
        SortableDoubleEncoder.append(output, next, words[0], words[1]);
        return SortableDoubleEncoder.trim(output.toByteArray());
    }

    private static int binaryExponent(final double value) {
        final int result;
        if (value < Double.MIN_NORMAL) {
            result = Math.getExponent(Math.scalb(value, 1022)) + 1 - 1022;
        } else {
            result = Math.getExponent(value) + 1;
        }
        return result;
    }

    private static double mantissa(final double value, final int binary) {
        final double result;
        if (value < Double.MIN_NORMAL) {
            final double scaled = Math.scalb(value, 1022);
            result = Math.scalb(scaled, -Math.getExponent(scaled) - 1);
        } else {
            result = Math.scalb(value, -binary);
        }
        return result;
    }

    private static int header(
        final int exponent, final boolean negative,
        final boolean expneg, final ByteArrayOutputStream output
    ) {
        int next = 0;
        if (!negative) {
            next = 0xE0;
        }
        if (expneg) {
            next ^= 0x60;
        }
        if (exponent < 8) {
            next ^= 0x20;
            next |= exponent << 2;
            if (negative ^ expneg) {
                next ^= 0x1C;
            }
        } else {
            next |= exponent >> 6;
            if (negative ^ expneg) {
                next ^= 0x1F;
            }
            output.write(next);
            next = exponent << 2;
            if (negative ^ expneg) {
                next ^= 0xFC;
            }
        }
        return next;
    }

    private static int[] words(final double mantissa, final boolean negative) {
        final int shift;
        if (negative) {
            shift = 26;
        } else {
            shift = 27;
        }
        double remainder = mantissa * (1 << shift);
        int high = (int) remainder;
        remainder -= high;
        int low = (int) (remainder * 4_294_967_296.0);
        if (negative) {
            high = -high;
            if (low != 0) {
                high += 1;
            }
            low = -low;
        }
        high &= 0x03FFFFFF;
        return new int[] {high, low};
    }

    private static void append(
        final ByteArrayOutputStream output, final int next,
        final int high, final int low
    ) {
        output.write(next | high >>> 24);
        output.write(high >>> 16);
        output.write(high >>> 8);
        output.write(high);
        output.write(low >>> 24);
        output.write(low >>> 16);
        output.write(low >>> 8);
        output.write(low);
    }

    private static byte[] trim(final byte[] bytes) {
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) {
            --length;
        }
        return Arrays.copyOf(bytes, length);
    }
}
