/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.error.RangeError;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link DatabaseStats}.
 *
 * @since 0.1.0
 */
final class DatabaseStatsTest {

    /**
     * Largest 32-bit value.
     */
    private static final long NARROW = 0xFFFF_FFFFL;

    /**
     * Statistics of a small database.
     */
    private static final DatabaseStats SMALL =
        new DatabaseStats(3L, 5L, 60L, 10L, 30L, 7L, 4L, 2L);

    @Test
    void encodesAsReference() {
        Assertions.assertEquals(
            "03020a0717023c04",
            HexFormat.of().formatHex(DatabaseStatsTest.SMALL.bytes()),
            "Count, docid gap, lower, wdf, upper gap, changes, total, spelling"
        );
    }

    @Test
    void encodesEmptyAsZeroBytes() {
        Assertions.assertEquals(
            "0000000000000000",
            HexFormat.of().formatHex(DatabaseStats.EMPTY.bytes()),
            "Eight zero bytes"
        );
    }

    @Test
    void parsesRoundTrip() {
        Assertions.assertEquals(
            DatabaseStatsTest.SMALL,
            DatabaseStats.parse(
                ByteReader.over(DatabaseStatsTest.SMALL.bytes()), false
            ),
            "Round trip"
        );
    }

    @Test
    void parsesNoBytesAsEmpty() {
        Assertions.assertEquals(
            DatabaseStats.EMPTY,
            DatabaseStats.parse(ByteReader.over(new byte[0]), false),
            "No statistics stored"
        );
    }

    @Test
    void parsesLargestValues() {
        final DatabaseStats stats = new DatabaseStats(
            DatabaseStatsTest.NARROW, DatabaseStatsTest.NARROW, Long.MAX_VALUE,
            DatabaseStatsTest.NARROW, DatabaseStatsTest.NARROW,
            DatabaseStatsTest.NARROW, DatabaseStatsTest.NARROW,
            DatabaseStatsTest.NARROW
        );
        Assertions.assertEquals(
            stats,
            DatabaseStats.parse(ByteReader.over(stats.bytes()), false),
            "32-bit and 63-bit values"
        );
    }

    @Test
    void parsesEmbeddedWithTrailingData() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("03020a0717023c04ff")
        );
        Assertions.assertEquals(
            List.of(DatabaseStatsTest.SMALL, false),
            List.of(DatabaseStats.parse(reader, true), reader.done()),
            "Trailing byte left for the tables"
        );
    }

    @Test
    void rejectsJunkAtEnd() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("03020a0717023c04ff")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> DatabaseStats.parse(reader, false),
            "Byte after the statistics"
        );
    }

    @Test
    void rejectsTruncatedStats() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("03020a0717023c")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> DatabaseStats.parse(reader, false),
            "Seven of eight values"
        );
    }

    @Test
    void rejectsDocidGapOverflow() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("ffffffff0f0100000000000000")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> DatabaseStats.parse(reader, false),
            "Last docid above 32 bits"
        );
    }

    @Test
    void wrapsLengthGapAsTheReference() {
        final DatabaseStats stats = DatabaseStats.parse(
            ByteReader.over(HexFormat.of().parseHex("000000ffffffff0f01000000")),
            false
        );
        Assertions.assertEquals(
            List.of(0L, DatabaseStatsTest.NARROW, "000000ffffffff0f01000000"),
            List.of(
                stats.upper(), stats.wdf(), HexFormat.of().formatHex(stats.bytes())
            ),
            "Length upper bound modulo two to the 32, stored back unchanged"
        );
    }

    @Test
    void rejectsTotalAboveLongRange() {
        final ByteReader reader = ByteReader.over(
            HexFormat.of().parseHex("000000000000ffffffffffffffffff0100")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> DatabaseStats.parse(reader, false),
            "Total length with bit 63 set"
        );
    }

    @Test
    void rejectsEncodingOutOfOrder() {
        Assertions.assertThrows(
            InvariantError.class,
            () -> new DatabaseStats(2L, 1L, 0L, 0L, 0L, 0L, 0L, 0L).bytes(),
            "Last docid below the count"
        );
    }

    @Test
    void encodesWdfBoundAboveLengthBoundAsTheReference() {
        Assertions.assertEquals(
            "00000002ffffffff0f000000",
            HexFormat.of().formatHex(
                new DatabaseStats(0L, 0L, 0L, 0L, 1L, 2L, 0L, 0L).bytes()
            ),
            "Difference of the bounds modulo two to the 32"
        );
    }

    @Test
    void rejectsValuesOutOfRange() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseStats(-1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
                "Negative count"
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseStats(
                    0L, DatabaseStatsTest.NARROW + 1L, 0L, 0L, 0L, 0L, 0L, 0L
                ),
                "Docid above 32 bits"
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseStats(0L, 0L, -1L, 0L, 0L, 0L, 0L, 0L),
                "Negative total"
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseStats(
                    0L, 0L, 0L, 0L, 0L, 0L, DatabaseStatsTest.NARROW + 1L, 0L
                ),
                "Spelling bound above 32 bits"
            )
        );
    }

    @Test
    void rejectsEveryFieldOutOfRange() {
        for (int pos = 0; pos < 8; ++pos) {
            final long[] values = new long[8];
            values[pos] = DatabaseStatsTest.NARROW + 1L;
            if (pos == 2) {
                values[pos] = -1L;
            }
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseStats(
                    values[0], values[1], values[2], values[3], values[4],
                    values[5], values[6], values[7]
                ),
                String.format("Field %d out of range", pos)
            );
        }
    }

    @Test
    void addsDocuments() {
        Assertions.assertEquals(
            new DatabaseStats(4L, 0L, 26L, 4L, 12L, 0L, 0L, 0L),
            DatabaseStats.EMPTY.added(10L).added(4L).added(0L).added(12L),
            "Count, total, non-zero lower bound and upper bound"
        );
    }

    @Test
    void keepsLowerBoundOnLongerDocument() {
        Assertions.assertEquals(
            new DatabaseStats(4L, 5L, 80L, 10L, 30L, 7L, 4L, 2L),
            DatabaseStatsTest.SMALL.added(20L),
            "Bounds unchanged inside the range"
        );
    }

    @Test
    void deletesDocuments() {
        Assertions.assertEquals(
            new DatabaseStats(2L, 5L, 40L, 10L, 30L, 7L, 4L, 2L),
            DatabaseStatsTest.SMALL.deleted(20L),
            "Count and total down, bounds kept"
        );
    }

    @Test
    void resetsBoundsOnceEmpty() {
        Assertions.assertEquals(
            new DatabaseStats(0L, 5L, 0L, 0L, 0L, 0L, 4L, 2L),
            DatabaseStatsTest.SMALL.deleted(20L).deleted(30L).deleted(10L),
            "Length and wdf bounds reset, docid and spelling kept"
        );
    }

    @Test
    void keepsCountOnEmptyDeletion() {
        Assertions.assertEquals(
            new DatabaseStats(2L, 5L, 60L, 10L, 30L, 7L, 4L, 2L),
            DatabaseStatsTest.SMALL.deleted(0L),
            "Document of length zero"
        );
    }

    @Test
    void rejectsDeletingMoreThanHeld() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                InvariantError.class, () -> DatabaseStats.EMPTY.deleted(0L),
                "No documents"
            ),
            () -> Assertions.assertThrows(
                InvariantError.class,
                () -> DatabaseStatsTest.SMALL.deleted(61L),
                "Length above the total"
            )
        );
    }

    @Test
    void raisesWdfBound() {
        Assertions.assertEquals(
            List.of(
                new DatabaseStats(3L, 5L, 60L, 10L, 30L, 9L, 4L, 2L),
                DatabaseStatsTest.SMALL, DatabaseStatsTest.SMALL
            ),
            List.of(
                DatabaseStatsTest.SMALL.checked(9L),
                DatabaseStatsTest.SMALL.checked(7L),
                DatabaseStatsTest.SMALL.checked(6L)
            ),
            "Only a greater frequency raises the bound"
        );
    }

    @Test
    void setsSingleValues() {
        Assertions.assertEquals(
            new DatabaseStats(3L, 9L, 60L, 10L, 30L, 7L, 8L, 6L),
            DatabaseStatsTest.SMALL.withLast(9L).withSpelling(8L)
                .withChangeset(6L),
            "Last docid, spelling bound, change set"
        );
    }

    @Test
    void boundsUniqueTerms() {
        Assertions.assertEquals(
            List.of(0L, 2L, 1L, 10L, 4L),
            List.of(
                DatabaseStats.EMPTY.uniqueTermsLowerBound(),
                DatabaseStatsTest.SMALL.uniqueTermsLowerBound(),
                DatabaseStatsTest.SMALL.checked(10L).uniqueTermsLowerBound(),
                new DatabaseStats(1L, 1L, 10L, 10L, 10L, 1L, 0L, 0L)
                    .uniqueTermsLowerBound(),
                new DatabaseStats(1L, 1L, 10L, 10L, 10L, 3L, 0L, 0L)
                    .uniqueTermsLowerBound()
            ),
            "Ceiling of the lower length bound over the wdf bound"
        );
    }

    @Test
    void rejectsUniqueTermsWithoutBounds() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> new DatabaseStats(1L, 1L, 10L, 0L, 10L, 1L, 0L, 0L)
                    .uniqueTermsLowerBound(),
                "Zero lower bound with a total"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> new DatabaseStats(1L, 1L, 10L, 10L, 10L, 0L, 0L, 0L)
                    .uniqueTermsLowerBound(),
                "Zero wdf bound with a total"
            )
        );
    }

    @Test
    void mergesStats() {
        Assertions.assertEquals(
            new DatabaseStats(5L, 5L, 160L, 8L, 50L, 9L, 10L, 2L),
            DatabaseStatsTest.SMALL.merged(
                new DatabaseStats(2L, 90L, 100L, 8L, 50L, 9L, 6L, 1L)
            ),
            "Sums, lowest lower bound, greatest upper bounds, own docid"
        );
    }

    @Test
    void mergesLowerBoundFromEither() {
        final DatabaseStats empty =
            new DatabaseStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        Assertions.assertEquals(
            List.of(10L, 10L, 0L, 10L),
            List.of(
                DatabaseStatsTest.SMALL.merged(empty).lower(),
                empty.merged(DatabaseStatsTest.SMALL).lower(),
                empty.merged(empty).lower(),
                DatabaseStatsTest.SMALL.merged(
                    new DatabaseStats(1L, 1L, 11L, 11L, 11L, 1L, 0L, 0L)
                ).lower()
            ),
            "Zero bounds ignored, smaller bound taken"
        );
    }

    @Test
    void rejectsMergeOverflow() {
        final DatabaseStats full = new DatabaseStats(
            DatabaseStatsTest.NARROW, DatabaseStatsTest.NARROW, Long.MAX_VALUE,
            1L, 1L, 1L, DatabaseStatsTest.NARROW, 0L
        );
        final DatabaseStats one =
            new DatabaseStats(1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L);
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                RangeError.class, () -> full.merged(one), "Count overflow"
            ),
            () -> Assertions.assertThrows(
                RangeError.class,
                () -> full.withSpelling(0L).merged(one.withSpelling(0L)),
                "Count overflow without spelling"
            ),
            () -> Assertions.assertThrows(
                RangeError.class,
                () -> new DatabaseStats(
                    0L, 0L, Long.MAX_VALUE, 0L, 0L, 0L, 0L, 0L
                ).merged(one),
                "Total overflow"
            ),
            () -> Assertions.assertThrows(
                RangeError.class,
                () -> new DatabaseStats(
                    0L, 0L, 0L, 0L, 0L, 0L, DatabaseStatsTest.NARROW, 0L
                ).merged(one),
                "Spelling overflow"
            )
        );
    }

    @Test
    void rejectsAddOverflow() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                RangeError.class,
                () -> new DatabaseStats(
                    DatabaseStatsTest.NARROW, DatabaseStatsTest.NARROW, 0L,
                    0L, 0L, 0L, 0L, 0L
                ).added(1L),
                "Count overflow"
            ),
            () -> Assertions.assertThrows(
                RangeError.class,
                () -> new DatabaseStats(
                    0L, 0L, Long.MAX_VALUE, 0L, 0L, 0L, 0L, 0L
                ).added(1L),
                "Total overflow"
            )
        );
    }
}
