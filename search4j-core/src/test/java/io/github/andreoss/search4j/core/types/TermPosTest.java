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
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
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
    void rejectsPositionsPastTheUnsignedRange() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new TermPos(0x1_0000_0000L),
            "Positions past 32 bits are invalid"
        );
    }

    @Test
    void holdsEveryUnsignedPosition() {
        Assertions.assertEquals(
            4_294_967_295L, TermPos.MAX.value(),
            "The largest position is the unsigned 32-bit maximum"
        );
        Assertions.assertEquals(
            2_147_483_648L, new TermPos(2_147_483_648L).value(),
            "Positions above the signed range are valid"
        );
    }

    @Test
    void wrapsToThirtyTwoBits() {
        Assertions.assertEquals(
            TermPos.MAX, TermPos.wrap(-1L), "Minus one wraps to the maximum"
        );
        Assertions.assertEquals(
            TermPos.FIRST, TermPos.wrap(0x1_0000_0000L),
            "Two to the thirty-second wraps to zero"
        );
    }

    @Test
    void parsesDecimalText() {
        Assertions.assertEquals(
            11, TermPos.parse("11").value(), "Decimal digits must parse"
        );
        Assertions.assertEquals(
            4_294_967_295L, TermPos.parse("4294967295").value(),
            "The unsigned 32-bit maximum must parse"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TermPos.parse("4294967296"),
            "One past the unsigned 32-bit range must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TermPos.parse("01"),
            "Leading zeros must be rejected"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> TermPos.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void advancesAndWraps() {
        Assertions.assertEquals(
            2, TermPos.FIRST.next().next().value(),
            "Next must advance by one"
        );
        Assertions.assertEquals(
            2_147_483_648L, new TermPos(Integer.MAX_VALUE).next().value(),
            "Next must pass the signed range"
        );
        Assertions.assertEquals(
            TermPos.FIRST, TermPos.MAX.next(),
            "Next must wrap past the maximum"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            TermPos.parse("3").compareTo(TermPos.parse("30")) < 0,
            "3 must sort before 30"
        );
        Assertions.assertTrue(
            TermPos.MAX.compareTo(new TermPos(Integer.MAX_VALUE)) > 0,
            "The maximum must sort after the signed maximum"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "5", TermPos.parse("5").toString(), "Text must be decimal"
        );
        Assertions.assertEquals(
            "4294967295", TermPos.MAX.toString(),
            "The maximum must render unsigned"
        );
    }
}
