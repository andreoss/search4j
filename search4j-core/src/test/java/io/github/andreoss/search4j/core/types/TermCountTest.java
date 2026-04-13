/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the term count value type.
 */
package io.github.andreoss.search4j.core.types;

import java.util.List;
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
            () -> TermCount.parse("4294967296"),
            "One past the unsigned 32-bit range must be rejected"
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
            List.of(9L, 0L, 1_705_032_704L, 4_294_967_295L),
            List.of(
                TermCount.parse("4").plus(TermCount.parse("5")).value(),
                TermCount.MAX.plus(TermCount.ONE).value(),
                TermCount.parse("3000000000")
                    .plus(TermCount.parse("3000000000")).value(),
                TermCount.parse("2147483648")
                    .plus(TermCount.parse("2147483647")).value()
            ),
            "Exact sums, sums past the maximum keep their low 32 bits"
        );
    }

    @Test
    void wrapsToLowBits() {
        Assertions.assertEquals(
            List.of(TermCount.MAX, TermCount.parse("3000000000")),
            List.of(TermCount.wrap(-1L), TermCount.wrap(7_294_967_296L)),
            "A negative difference wraps below the maximum, a sum past it"
        );
    }

    @Test
    void subtractsCounts() {
        Assertions.assertEquals(
            List.of(TermCount.parse("2000000000"), TermCount.MAX),
            List.of(
                TermCount.parse("3000000000").minus(TermCount.parse("1000000000")),
                TermCount.ZERO.minus(TermCount.ONE)
            ),
            "Exact differences, differences below zero wrap"
        );
    }

    @Test
    void holdsUnsignedRange() {
        Assertions.assertEquals(
            List.of(4_294_967_295L, TermCount.MAX),
            List.of(
                TermCount.parse("4294967295").value(), TermCount.parse("4294967295")
            ),
            "The unsigned 32-bit maximum parses and is the maximum"
        );
    }

    @Test
    void rejectsCountsPastTheUnsignedRange() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new TermCount(4_294_967_296L),
            "Counts past the unsigned 32-bit range are invalid"
        );
    }

    @Test
    void ordersAboveSignedRange() {
        Assertions.assertEquals(
            List.of(true, "3000000000"),
            List.of(
                TermCount.parse("2147483647")
                    .compareTo(TermCount.parse("3000000000")) < 0,
                TermCount.parse("3000000000").toString()
            ),
            "Counts above 2^31 sort above 2^31-1, text is unsigned decimal"
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
