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
    void rejectsNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new TotalLength(-1L),
            "Negative totals are invalid"
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
            () -> TotalLength.parse("99999999999999999999"),
            "A value past the long range must be rejected"
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
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new TotalLength(Long.MAX_VALUE).plus(new TotalLength(1L)),
            "Sums must not overflow"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            TotalLength.parse("2").compareTo(TotalLength.parse("20")) < 0,
            "2 must sort before 20"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "77", TotalLength.parse("77").toString(), "Text must be decimal"
        );
    }
}
