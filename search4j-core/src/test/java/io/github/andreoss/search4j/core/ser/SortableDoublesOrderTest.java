/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Byte order of sortable doubles follows numeric order, as the reference
 * encoding promises.
 *
 * @since 0.1.0
 */
final class SortableDoublesOrderTest {

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("pairs")
    void ordersLikeTheNumbers(final double left, final double right) {
        Assertions.assertEquals(
            left <= right, SortableDoubles.ordered(left, right),
            String.format("%s before %s", left, right)
        );
    }

    private static Stream<Arguments> pairs() {
        return Stream.of(
            Arguments.of(1.0, 1.0),
            Arguments.of(2.0, 1.0),
            Arguments.of(1.0, 2.0),
            Arguments.of(1024.0, 1025.0),
            Arguments.of(1025.0, 1024.0),
            Arguments.of(0.5, 0.75),
            Arguments.of(0.75, 0.5),
            Arguments.of(-1.0, -0.998_046_875),
            Arguments.of(-0.998_046_875, -1.0),
            Arguments.of(3.5, 3.5),
            Arguments.of(1e300, 1e10),
            Arguments.of(Double.NEGATIVE_INFINITY, -1e300),
            Arguments.of(-1e300, Double.NEGATIVE_INFINITY),
            Arguments.of(Double.POSITIVE_INFINITY, Double.MAX_VALUE)
        );
    }
}
