/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

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
}
