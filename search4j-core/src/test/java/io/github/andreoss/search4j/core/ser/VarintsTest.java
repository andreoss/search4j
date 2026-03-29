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
 * Tests of {@link Varints}.
 *
 * @since 0.1.0
 */
final class VarintsTest {

    @Test
    void roundTripsZero() {
        Assertions.assertEquals(
            0L, Varints.unpack(Varints.pack(0L)), "Zero round trip"
        );
    }

    @Test
    void roundTripsSmallValue() {
        Assertions.assertEquals(
            127L, Varints.unpack(Varints.pack(127L)), "Small round trip"
        );
    }

    @Test
    void roundTripsMultiByteValue() {
        Assertions.assertEquals(
            300L, Varints.unpack(Varints.pack(300L)), "Multi-byte round trip"
        );
    }

    @Test
    void roundTripsLargeValue() {
        Assertions.assertEquals(
            1_000_000L,
            Varints.unpack(Varints.pack(1_000_000L)),
            "Large round trip"
        );
    }

    @Test
    void rejectsNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> Varints.pack(-1L),
            "Negative rejected"
        );
    }
}
