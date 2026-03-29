/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SortableDoubles}.
 *
 * @since 0.1.0
 */
final class SortableDoublesTest {

    @Test
    void roundTripsPositive() {
        Assertions.assertEquals(
            3.5,
            SortableDoubles.deserialise(SortableDoubles.serialise(3.5)),
            "Positive round trip"
        );
    }

    @Test
    void roundTripsNegative() {
        Assertions.assertEquals(
            -2.25,
            SortableDoubles.deserialise(SortableDoubles.serialise(-2.25)),
            "Negative round trip"
        );
    }

    @Test
    void roundTripsZero() {
        Assertions.assertEquals(
            0.0,
            SortableDoubles.deserialise(SortableDoubles.serialise(0.0)),
            "Zero round trip"
        );
    }

    @Test
    void preservesOrderForPositives() {
        Assertions.assertTrue(
            SortableDoubles.ordered(1.0, 9.0), "Positive order"
        );
    }

    @Test
    void preservesOrderForNegatives() {
        Assertions.assertTrue(
            SortableDoubles.ordered(-9.0, -1.0), "Negative order"
        );
    }

    @Test
    void preservesOrderAcrossZero() {
        Assertions.assertTrue(
            SortableDoubles.ordered(-0.5, 0.5), "Cross-zero order"
        );
    }
}
