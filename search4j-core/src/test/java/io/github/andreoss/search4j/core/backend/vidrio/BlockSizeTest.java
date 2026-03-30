/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link BlockSize}.
 *
 * @since 0.1.0
 */
final class BlockSizeTest {

    @Test
    void definesLimits() {
        Assertions.assertEquals(
            List.of(2048, 65_536, 8192),
            List.of(
                BlockSize.MIN.bytes(), BlockSize.MAX.bytes(),
                BlockSize.DEFAULT.bytes()
            ),
            "Min, max and default"
        );
    }

    @Test
    void acceptsEveryPowerOfTwoInRange() {
        Assertions.assertEquals(
            List.of(2048, 4096, 8192, 16_384, 32_768, 65_536),
            IntStream.rangeClosed(0, 5).mapToObj(BlockSize::fromShift)
                .map(BlockSize::bytes).toList(),
            "Shifts 0 to 5"
        );
    }

    @Test
    void reportsShift() {
        Assertions.assertEquals(
            List.of(0, 2, 5),
            List.of(
                BlockSize.MIN.shift(), BlockSize.DEFAULT.shift(),
                BlockSize.MAX.shift()
            ),
            "Shift over minimum"
        );
    }

    @Test
    void rejectsBadSizes() {
        for (final int bytes : new int[] {1024, 3000, 131_072, 0, -2048}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new BlockSize(bytes),
                String.format("Size %d", bytes)
            );
        }
    }

    @Test
    void rejectsBadShifts() {
        for (final int shift : new int[] {-1, 6}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> BlockSize.fromShift(shift),
                String.format("Shift %d", shift)
            );
        }
    }

    @Test
    void fallsBackToDefault() {
        Assertions.assertEquals(
            List.of(BlockSize.DEFAULT, BlockSize.DEFAULT, BlockSize.MAX),
            List.of(
                BlockSize.orDefault(1024), BlockSize.orDefault(5000),
                BlockSize.orDefault(65_536)
            ),
            "Invalid requests use the default"
        );
    }

    @Test
    void keepsValidRequest() {
        Assertions.assertEquals(
            new BlockSize(4096), BlockSize.orDefault(4096), "Valid request"
        );
    }
}
