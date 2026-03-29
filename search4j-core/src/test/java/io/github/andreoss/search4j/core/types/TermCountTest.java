/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the term count value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermCount}.
 *
 * @since 0.1.0
 */
final class TermCountTest {

    @Test
    void parsesDecimalCount() {
        Assertions.assertEquals(
            3, TermCount.parse("3").value(), "Decimal digits must parse"
        );
    }

    @Test
    void zeroCount() {
        Assertions.assertEquals(0, TermCount.ZERO.value(), "Zero count");
    }

    @Test
    void rejectsNegativeCounts() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new TermCount(-1),
            "Negative counts are invalid"
        );
    }

    @Test
    void rejectsMalformedText() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TermCount.parse("-2"),
            "A negative sign must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TermCount.parse("2147483648"),
            "One past the signed 32-bit range must be rejected"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> TermCount.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void addsCounts() {
        Assertions.assertEquals(
            9,
            TermCount.parse("4").plus(TermCount.parse("5")).value(),
            "Sums must be exact"
        );
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new TermCount(Integer.MAX_VALUE).plus(new TermCount(1)),
            "Sums must not overflow"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            TermCount.parse("2").compareTo(TermCount.parse("10")) < 0,
            "2 must sort before 10"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "8", TermCount.parse("8").toString(), "Text must be decimal"
        );
    }
}
