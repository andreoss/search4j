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
 * Tests of {@link BitWriter} and {@link BitReader}.
 *
 * @since 0.1.0
 */
final class BitStreamTest {

    @Test
    void roundTripsAlignedBits() {
        final BitWriter writer = new BitWriter();
        writer.write(5L, 3);
        writer.write(7L, 3);
        Assertions.assertEquals(
            5L, new BitReader(writer.finish()).read(3), "First value"
        );
    }

    @Test
    void roundTripsSecondValue() {
        final BitWriter writer = new BitWriter();
        writer.write(5L, 3);
        writer.write(7L, 3);
        final BitReader reader = new BitReader(writer.finish());
        reader.read(3);
        Assertions.assertEquals(7L, reader.read(3), "Second value");
    }

    @Test
    void roundTripsUnalignedBits() {
        final BitWriter writer = new BitWriter();
        writer.write(13L, 5);
        writer.write(2L, 4);
        Assertions.assertEquals(
            13L, new BitReader(writer.finish()).read(5), "Unaligned first"
        );
    }
}
