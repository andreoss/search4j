/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TableState}, {@link FreeListInfo} and
 * {@link FreeListMark}.
 *
 * @since 0.1.0
 */
final class TableStateTest {

    @Test
    void encodesNewTableAsReference() {
        Assertions.assertEquals(
            List.of("000300010000", "000300011200"),
            List.of(
                HexFormat.of().formatHex(
                    TableState.initial(BlockSize.MIN, 0).bytes()
                ),
                HexFormat.of().formatHex(
                    TableState.initial(BlockSize.MIN, 18).bytes()
                )
            ),
            "Root information of tables never written"
        );
    }

    @Test
    void encodesDeepTable() {
        Assertions.assertEquals(
            "ac020ec8012080040ea40381018040ff01ffffffff0f0f",
            HexFormat.of().formatHex(
                new TableState(
                    new TableRoot(
                        new BlockNumber(300L), 3, BlockSize.MAX,
                        new RevisionNumber(2L), false
                    ),
                    new EntryCount(200L), true, 512,
                    new FreeListInfo(
                        new RevisionNumber(420L), new BlockNumber(129L),
                        new FreeListMark(new BlockNumber(0x2000L), 1020),
                        new FreeListMark(new BlockNumber(BlockNumber.MAX), 60)
                    )
                ).bytes()
            ),
            "Level, flags, size shift and free list"
        );
    }

    @Test
    void encodesEmptyFreeListAsNothing() {
        Assertions.assertArrayEquals(
            new byte[0], FreeListInfo.EMPTY.bytes(), "Never committed"
        );
    }

    @Test
    void parsesRoundTripOfNewTable() {
        final TableState state =
            TableState.initial(BlockSize.MIN, TableType.COMPRESS_MIN);
        Assertions.assertEquals(
            state,
            TableState.parse(
                ByteReader.over(state.bytes()), new RevisionNumber(0L)
            ),
            "Round trip"
        );
    }

    @Test
    void parsesRoundTripOfDeepTable() {
        final TableState state = new TableState(
            new TableRoot(
                new BlockNumber(300L), 3, BlockSize.MAX,
                new RevisionNumber(2L), false
            ),
            new EntryCount(200L), true, 512,
            new FreeListInfo(
                new RevisionNumber(420L), new BlockNumber(129L),
                new FreeListMark(new BlockNumber(0x2000L), 1020),
                new FreeListMark(new BlockNumber(BlockNumber.MAX), 60)
            )
        );
        final ByteReader reader = ByteReader.over(state.bytes());
        Assertions.assertEquals(
            List.of(state, true),
            List.of(
                TableState.parse(reader, new RevisionNumber(2L)),
                reader.done()
            ),
            "Round trip of every field"
        );
    }

    @Test
    void parsesNonSequentialRealRoot() {
        final TableState state = new TableState(
            new TableRoot(
                new BlockNumber(7L), 0, BlockSize.DEFAULT,
                new RevisionNumber(9L), false
            ),
            new EntryCount(1L), false, 0, FreeListInfo.EMPTY
        );
        Assertions.assertEquals(
            state,
            TableState.parse(
                ByteReader.over(state.bytes()), new RevisionNumber(9L)
            ),
            "Flags clear"
        );
    }

    @Test
    void parsesLargestEntryCount() {
        final TableState state = new TableState(
            TableRoot.empty(BlockSize.MIN, new RevisionNumber(0L)),
            new EntryCount(Long.MAX_VALUE), true, 0, FreeListInfo.EMPTY
        );
        Assertions.assertEquals(
            state,
            TableState.parse(
                ByteReader.over(state.bytes()), new RevisionNumber(0L)
            ),
            "63-bit count"
        );
    }

    @Test
    void mapsOldCompressionThreshold() {
        Assertions.assertEquals(
            TableType.COMPRESS_MIN,
            TableState.parse(
                ByteReader.over(TableStateTest.hex("000300010400")),
                new RevisionNumber(0L)
            ).compress(),
            "Threshold 4 of old databases"
        );
    }

    @Test
    void rejectsDeepRoot() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableState.parse(
                ByteReader.over(TableStateTest.hex("002800010000")),
                new RevisionNumber(0L)
            ),
            "Level 10"
        );
    }

    @Test
    void acceptsDeepestRoot() {
        Assertions.assertEquals(
            9,
            TableState.parse(
                ByteReader.over(TableStateTest.hex("002400010000")),
                new RevisionNumber(0L)
            ).root().level(),
            "Level 9"
        );
    }

    @Test
    void rejectsFakeRootAboveLeaves() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableState.parse(
                ByteReader.over(TableStateTest.hex("000700010000")),
                new RevisionNumber(0L)
            ),
            "Fake root at level 1"
        );
    }

    @Test
    void rejectsBadBlockSizes() {
        for (final String shift : List.of("00", "03", "40", "8001")) {
            final String hex = String.format("000300%s0000", shift);
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> TableStateTest.parse(hex),
                String.format("Size shift %s", shift)
            );
        }
    }

    @Test
    void acceptsEveryBlockSize() {
        for (int shift = 0; shift <= 5; ++shift) {
            Assertions.assertEquals(
                BlockSize.fromShift(shift),
                TableStateTest.parse(
                    String.format("000300%02x0000", 1 << shift)
                ).root().size(),
                String.format("Shift %d", shift)
            );
        }
    }

    @Test
    void rejectsOverflowingEntryCount() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableState.parse(
                ByteReader.over(
                    TableStateTest.hex("0003ffffffffffffffffff01010000")
                ),
                new RevisionNumber(0L)
            ),
            "Count with bit 63 set"
        );
    }

    @Test
    void rejectsOverflowingThreshold() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableState.parse(
                ByteReader.over(TableStateTest.hex("00030001ffffffff0f00")),
                new RevisionNumber(0L)
            ),
            "Threshold above the int range"
        );
    }

    @Test
    void rejectsTruncatedRecord() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> TableState.parse(
                ByteReader.over(TableStateTest.hex("0003000100")),
                new RevisionNumber(0L)
            ),
            "Free list length missing"
        );
    }

    @Test
    void parsesFreeListRoundTrip() {
        final FreeListInfo info = new FreeListInfo(
            new RevisionNumber(RevisionNumber.MAX), new BlockNumber(5L),
            new FreeListMark(new BlockNumber(2L), 8),
            new FreeListMark(new BlockNumber(3L), 65_532)
        );
        Assertions.assertEquals(
            List.of(info, FreeListInfo.EMPTY),
            List.of(
                FreeListInfo.parse(info.bytes()),
                FreeListInfo.parse(new byte[0])
            ),
            "Round trip and the empty list"
        );
    }

    @Test
    void rejectsFreeListWithJunk() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> FreeListInfo.parse(TableStateTest.hex("00010000000000")),
            "Seven bytes"
        );
    }

    @Test
    void rejectsTruncatedFreeList() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> FreeListInfo.parse(TableStateTest.hex("0001000000")),
            "Five bytes"
        );
    }

    @Test
    void rejectsFreeListOffsetOverflow() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> FreeListInfo.parse(
                TableStateTest.hex("000100ffffffff0f0000")
            ),
            "Offset above the int range once scaled"
        );
    }

    @Test
    void parsesLargestFreeListOffset() {
        Assertions.assertEquals(
            Integer.MAX_VALUE - 3,
            FreeListInfo.parse(
                TableStateTest.hex("000100ffffffff010000")
            ).head().offset(),
            "Largest offset fitting an int"
        );
    }

    @Test
    void rejectsNegativeThreshold() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> TableState.initial(BlockSize.MIN, -1),
            "Negative compression threshold"
        );
    }

    @Test
    void rejectsUnalignedFreeListOffset() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new FreeListMark(new BlockNumber(1L), 6),
            "Offset not a multiple of four"
        );
    }

    @Test
    void rejectsNegativeFreeListOffset() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new FreeListMark(new BlockNumber(1L), -4),
            "Negative offset"
        );
    }

    private static TableState parse(final String hex) {
        return TableState.parse(
            ByteReader.over(TableStateTest.hex(hex)), new RevisionNumber(0L)
        );
    }

    private static byte[] hex(final String text) {
        return HexFormat.of().parseHex(text);
    }
}
