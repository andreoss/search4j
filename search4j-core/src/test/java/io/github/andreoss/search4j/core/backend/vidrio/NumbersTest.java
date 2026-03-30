/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link BlockNumber}, {@link RevisionNumber} and {@link EntryCount}.
 *
 * @since 0.1.0
 */
final class NumbersTest {

    @Test
    void readsUnsignedBlockNumbers() {
        Assertions.assertEquals(
            List.of(4_294_967_295L, -1, 7L),
            List.of(
                BlockNumber.fromBits(-1).value(),
                new BlockNumber(BlockNumber.MAX).bits(),
                BlockNumber.fromBits(7).value()
            ),
            "Unsigned 32 bits"
        );
    }

    @Test
    void readsUnsignedRevisions() {
        Assertions.assertEquals(
            List.of(4_294_967_295L, -1, 1L),
            List.of(
                RevisionNumber.fromBits(-1).value(),
                new RevisionNumber(RevisionNumber.MAX).bits(),
                RevisionNumber.fromBits(1).value()
            ),
            "Unsigned 32 bits"
        );
    }

    @Test
    void rejectsOutOfRangeNumbers() {
        for (final long value : new long[] {-1L, 0x1_0000_0000L}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new BlockNumber(value),
                String.format("Block %d", value)
            );
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RevisionNumber(value),
                String.format("Revision %d", value)
            );
        }
    }

    @Test
    void ordersNumbers() {
        Assertions.assertEquals(
            List.of(-1, 1, 0, -1),
            List.of(
                new BlockNumber(1L).compareTo(new BlockNumber(2L)),
                new RevisionNumber(BlockNumber.MAX)
                    .compareTo(new RevisionNumber(0L)),
                new RevisionNumber(3L).compareTo(new RevisionNumber(3L)),
                new RevisionNumber(0L).compareTo(new RevisionNumber(1L))
            ),
            "Numeric order"
        );
    }

    @Test
    void printsDecimal() {
        Assertions.assertEquals(
            List.of("4294967295", "12"),
            List.of(
                BlockNumber.fromBits(-1).toString(),
                new RevisionNumber(12L).toString()
            ),
            "Decimal text"
        );
    }

    @Test
    void countsEntries() {
        Assertions.assertEquals(
            List.of(true, false),
            List.of(EntryCount.EMPTY.empty(), new EntryCount(1L).empty()),
            "Empty only at zero"
        );
    }

    @Test
    void acceptsRangeEnds() {
        Assertions.assertEquals(
            List.of(0L, 0L, 0L),
            List.of(
                new BlockNumber(0L).value(), new RevisionNumber(0L).value(),
                new EntryCount(0L).value()
            ),
            "Zero accepted"
        );
    }

    @Test
    void rejectsNegativeCount() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new EntryCount(-1L),
            "Negative count"
        );
    }
}
