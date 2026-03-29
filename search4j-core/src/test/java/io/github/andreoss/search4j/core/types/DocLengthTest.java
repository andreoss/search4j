/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the normalised document length value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link DocLength}.
 *
 * @since 0.1.0
 */
final class DocLengthTest {

    @Test
    void parsesDecimalLength() {
        Assertions.assertEquals(
            1.5, DocLength.parse("1.5").value(), 0.0, "Decimal parses"
        );
    }

    @Test
    void zeroLength() {
        Assertions.assertEquals(0.0, DocLength.ZERO.value(), 0.0, "Zero length");
    }

    @Test
    void rejectsNonFiniteAndNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new DocLength(-0.5),
            "Negative lengths are invalid"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new DocLength(Double.NaN),
            "NaN is invalid"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new DocLength(Double.POSITIVE_INFINITY),
            "Infinity is invalid"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            DocLength.parse("0.5").compareTo(DocLength.parse("2.0")) < 0,
            "0.5 must sort before 2.0"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "2.25", DocLength.parse("2.25").toString(), "Text must be decimal"
        );
    }
}
