/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the total document length value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TotalLength}.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class TotalLengthTest {

    @Test
    void parsesDecimalTotal() {
        Assertions.assertEquals(
            100L, TotalLength.parse("100").value(), "Decimal digits must parse"
        );
    }

    @Test
    void zeroTotal() {
        Assertions.assertEquals(0L, TotalLength.ZERO.value(), "Zero total");
    }

    @Test
    void holdsUnsignedTotals() {
        Assertions.assertEquals(
            "18446744073709551615", new TotalLength(-1L).toString(),
            "Every bit pattern is an unsigned total"
        );
        Assertions.assertEquals(
            Long.MIN_VALUE, TotalLength.parse("9223372036854775808").value(),
            "Two to the sixty-third must parse"
        );
        Assertions.assertEquals(
            -1L, TotalLength.parse("18446744073709551615").value(),
            "The unsigned 64-bit maximum must parse"
        );
    }

    @Test
    void convertsToDoubleAsUnsigned() {
        Assertions.assertEquals(
            0x1p63, new TotalLength(Long.MIN_VALUE).asDouble(),
            "Two to the sixty-third must convert exactly"
        );
        Assertions.assertEquals(
            0x1p64, new TotalLength(-1L).asDouble(),
            "The maximum must round up to two to the sixty-fourth"
        );
        Assertions.assertEquals(
            0x1p63, new TotalLength(Long.MIN_VALUE + 1024L).asDouble(),
            "A tie must round to even"
        );
        Assertions.assertEquals(
            0x1p63 + 2048.0, new TotalLength(Long.MIN_VALUE + 1025L).asDouble(),
            "Past a tie must round up"
        );
        Assertions.assertEquals(
            12_345.0, new TotalLength(12_345L).asDouble(),
            "Small totals must convert exactly"
        );
    }

    @Test
    void clampsToTheCountRange() {
        Assertions.assertEquals(
            TermCount.MAX, new TotalLength(Long.MIN_VALUE).clamped(),
            "A huge total must clamp to the count maximum"
        );
        Assertions.assertEquals(
            TermCount.MAX, new TotalLength(0x1_0000_0000L).clamped(),
            "A total past the count range must clamp"
        );
        Assertions.assertEquals(
            new TermCount(7L), new TotalLength(7L).clamped(),
            "A small total must be kept"
        );
    }

    @Test
    void rejectsMalformedText() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TotalLength.parse("-9"),
            "A negative sign must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TotalLength.parse("18446744073709551616"),
            "A value past the unsigned 64-bit range must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TotalLength.parse("018"),
            "Leading zeros must be rejected"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> TotalLength.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void addsTotals() {
        Assertions.assertEquals(
            12L,
            TotalLength.parse("5").plus(TotalLength.parse("7")).value(),
            "Sums must be exact"
        );
        Assertions.assertEquals(
            Long.MIN_VALUE,
            new TotalLength(Long.MAX_VALUE).plus(new TotalLength(1L)).value(),
            "Sums must pass the signed range"
        );
        Assertions.assertEquals(
            0L, new TotalLength(-1L).plus(new TotalLength(1L)).value(),
            "Sums must wrap modulo two to the sixty-fourth"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            TotalLength.parse("2").compareTo(TotalLength.parse("20")) < 0,
            "2 must sort before 20"
        );
        Assertions.assertTrue(
            new TotalLength(Long.MIN_VALUE).compareTo(new TotalLength(Long.MAX_VALUE)) > 0,
            "Totals must order as unsigned"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "77", TotalLength.parse("77").toString(), "Text must be decimal"
        );
    }
}
