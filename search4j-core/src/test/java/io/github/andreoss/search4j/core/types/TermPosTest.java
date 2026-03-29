/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the term position value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermPos}.
 *
 * @since 0.1.0
 */
final class TermPosTest {

    @Test
    void startsAtZero() {
        Assertions.assertEquals(
            0, TermPos.FIRST.value(), "The first position is zero"
        );
    }

    @Test
    void rejectsNegativePositions() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new TermPos(-1),
            "Negative positions are invalid"
        );
    }

    @Test
    void parsesDecimalText() {
        Assertions.assertEquals(
            11, TermPos.parse("11").value(), "Decimal digits must parse"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TermPos.parse("2147483648"),
            "One past the signed 32-bit range must be rejected"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> TermPos.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void advancesAndDetectsOverflow() {
        Assertions.assertEquals(
            2, TermPos.FIRST.next().next().value(),
            "Next must advance by one"
        );
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new TermPos(Integer.MAX_VALUE).next(),
            "Next must not overflow"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            TermPos.parse("3").compareTo(TermPos.parse("30")) < 0,
            "3 must sort before 30"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "5", TermPos.parse("5").toString(), "Text must be decimal"
        );
    }
}
