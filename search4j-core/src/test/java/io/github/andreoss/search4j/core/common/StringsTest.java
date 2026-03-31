/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the common utilities.
 */
package io.github.andreoss.search4j.core.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests of {@link Strings}.
 *
 * @since 0.1.0
 */
final class StringsTest {

    @Test
    void nullIsEmpty() {
        Assertions.assertTrue(Strings.empty(null), "Null is empty");
    }

    @Test
    void blankIsEmpty() {
        Assertions.assertTrue(Strings.empty(""), "Blank is empty");
    }

    @Test
    void textIsNotEmpty() {
        Assertions.assertFalse(Strings.empty("x"), "Text is not empty");
    }

    @Test
    void nullBecomesEmptyString() {
        Assertions.assertEquals("", Strings.orEmpty(null), "Null map");
    }

    @Test
    void textPassesThrough() {
        Assertions.assertEquals("y", Strings.orEmpty("y"), "Text map");
    }

    @ParameterizedTest
    @CsvSource({
        "1.25, 1.25",
        "42, 42",
        "0.5, 0.5",
        "0, 0",
        "-0.0, -0",
        "3, 3",
        "100, 100",
        "-2.5, -2.5",
        "0.1, 0.10000000000000000555",
        "0.001, 0.0010000000000000000208",
        "0.0001, 0.00010000000000000000479",
        "123.456, 123.45600000000000307",
        "1e15, 1000000000000000",
        "1e19, 10000000000000000000",
        "1e20, 1e+20",
        "1e21, 1e+21",
        "1.5e21, 1.5e+21",
        "-1e22, -1e+22",
        "1e-5, 1.0000000000000000818e-05",
        "1e100, 1.0000000000000000159e+100",
        "Infinity, inf",
        "-Infinity, -inf",
        "NaN, nan",
        "4.9e-324, 4.9406564584124654418e-324",
        "1.7976931348623157e308, 1.7976931348623157081e+308"
    })
    void rendersDoubles(final double value, final String expected) {
        Assertions.assertEquals(
            expected, Strings.decimal(value), "Twenty significant digits"
        );
    }

    @Test
    void rendersThirds() {
        Assertions.assertEquals(
            "0.33333333333333331483", Strings.decimal(1.0 / 3.0),
            "Rounded to twenty digits"
        );
    }

    @Test
    void rendersTwoThirds() {
        Assertions.assertEquals(
            "0.66666666666666662966", Strings.decimal(2.0 / 3.0),
            "Rounded half up on the exact expansion"
        );
    }
}
