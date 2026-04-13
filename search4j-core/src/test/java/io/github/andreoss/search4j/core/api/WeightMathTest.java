/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link WeightMath}.
 *
 * @since 0.8.0
 */
final class WeightMathTest {

    @Test
    void takesBaseTwoLogarithmsAsTheReference() throws IOException {
        int rows = 0;
        int differ = 0;
        long most = 0L;
        for (final String[] row : WeightVectors.rows("log2-vectors.tsv")) {
            rows = rows + 1;
            final long expected = Long.parseUnsignedLong(row[1], 16);
            final long actual = Double.doubleToRawLongBits(
                WeightMath.logTwo(
                    Double.longBitsToDouble(Long.parseUnsignedLong(row[0], 16))
                )
            );
            if (actual != expected) {
                differ = differ + 1;
                most = Math.max(most, Math.abs(actual - expected));
            }
        }
        Assertions.assertEquals(
            List.of(5000, 2, 1L), List.of(rows, differ, most),
            "Bit patterns of the reference, two off by one unit in the last place"
        );
    }

    @Test
    void keepsExactAndSpecialLogarithms() {
        Assertions.assertEquals(
            List.of(
                3.0, -1.0, 0.0, 31.0, Double.NEGATIVE_INFINITY, Double.NaN,
                Double.POSITIVE_INFINITY, -1074.0
            ),
            List.of(
                WeightMath.logTwo(8.0), WeightMath.logTwo(0.5),
                WeightMath.logTwo(1.0), WeightMath.logTwo(2_147_483_648.0),
                WeightMath.logTwo(0.0), WeightMath.logTwo(-1.0),
                WeightMath.logTwo(Double.POSITIVE_INFINITY),
                WeightMath.logTwo(Double.MIN_VALUE)
            ),
            "Powers of two, zero, negative, infinite and subnormal arguments"
        );
    }

    @Test
    void wrapsCountArithmeticToThirtyTwoBits() {
        Assertions.assertEquals(
            List.of(0.0, 4_294_967_295.0, 1_705_032_704.0, 3_000_000_000.0),
            List.of(
                WeightMath.unsigned(65_536L * 65_536L), WeightMath.unsigned(-1L),
                WeightMath.unsigned(3_000_000_000L * 2L),
                WeightMath.unsigned(3_000_000_000L)
            ),
            "Products and sums keep their low 32 bits as unsigned counts"
        );
    }
}
