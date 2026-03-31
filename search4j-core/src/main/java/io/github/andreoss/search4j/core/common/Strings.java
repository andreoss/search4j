/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Common utilities of the search engine.
 */
package io.github.andreoss.search4j.core.common;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * String helpers used by the common layer.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Strings {

    /**
     * Significant digits of a decimal rendering.
     */
    private static final int DIGITS = 20;

    /**
     * Bits of the fraction of a double.
     */
    private static final int FRACTION = 52;

    /**
     * Utility class, no instances.
     */
    private Strings() {
    }

    /**
     * Tests whether a text is null or empty.
     *
     * @param text Text to test, may be null
     * @return True for null or the empty string
     */
    public static boolean empty(final String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Replaces null with the empty string.
     *
     * @param text Text to normalise, may be null
     * @return The text, or the empty string for null
     */
    public static String orEmpty(final String text) {
        final String result;
        if (text == null) {
            result = "";
        } else {
            result = text;
        }
        return result;
    }

    /**
     * Shortest decimal text of a number rounded to twenty significant
     * digits, as the reference renders a double: plain notation when the
     * exponent lies between -4 and 19, otherwise a mantissa and a signed
     * exponent of at least two digits.
     *
     * @param value Number to render
     * @return Decimal text, {@code inf} or {@code nan} when not finite
     */
    public static String decimal(final double value) {
        final String text;
        if (Double.isNaN(value)) {
            text = "nan";
        } else if (Double.isInfinite(value)) {
            text = Strings.signed(value < 0, "inf");
        } else if (value == 0.0) {
            text = Strings.signed(1.0 / value < 0, "0");
        } else {
            text = Strings.rounded(value);
        }
        return text;
    }

    private static String signed(final boolean negative, final String text) {
        final String result;
        if (negative) {
            result = String.format("-%s", text);
        } else {
            result = text;
        }
        return result;
    }

    private static String rounded(final double value) {
        final BigDecimal number = Strings.exact(Math.abs(value))
            .round(new MathContext(Strings.DIGITS, RoundingMode.HALF_EVEN))
            .stripTrailingZeros();
        final int exponent = number.precision() - number.scale() - 1;
        final String text;
        if (exponent < -4 || exponent >= Strings.DIGITS) {
            final String digits = number.unscaledValue().toString();
            final StringBuilder out = new StringBuilder(digits.length() + 8)
                .append(digits.charAt(0));
            if (digits.length() > 1) {
                out.append('.').append(digits.substring(1));
            }
            out.append(String.format("e%+03d", exponent));
            text = out.toString();
        } else {
            text = number.toPlainString();
        }
        return Strings.signed(value < 0, text);
    }

    private static BigDecimal exact(final double value) {
        final long bits = Double.doubleToLongBits(value);
        final int biased = (int) (bits >> Strings.FRACTION);
        final long fraction = bits & ((1L << Strings.FRACTION) - 1);
        final long mantissa;
        final int shift;
        if (biased == 0) {
            mantissa = fraction;
            shift = 1 - 1023 - Strings.FRACTION;
        } else {
            mantissa = fraction | (1L << Strings.FRACTION);
            shift = biased - Strings.FRACTION - 1023;
        }
        final BigDecimal digits = new BigDecimal(BigInteger.valueOf(mantissa));
        final BigDecimal result;
        if (shift < 0) {
            result = digits.divide(new BigDecimal(BigInteger.TWO.pow(-shift)));
        } else {
            result = digits.multiply(new BigDecimal(BigInteger.TWO.pow(shift)));
        }
        return result;
    }
}
