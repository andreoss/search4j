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
 * Tests of {@link BlockHeader}.
 *
 * @since 0.1.0
 */
final class BlockHeaderTest {

    /**
     * Header of a leaf block with two items.
     */
    private static final BlockHeader LEAF = new BlockHeader(
        new RevisionNumber(1L), 0, 0x0705, 0x0705, 15
    );

    @Test
    void encodesElevenBytesBigEndian() {
        Assertions.assertEquals(
            "000000010007050705000f",
            HexFormat.of().formatHex(BlockHeaderTest.LEAF.bytes()),
            "Header bytes"
        );
    }

    @Test
    void hasDirectoryStartSize() {
        Assertions.assertEquals(
            List.of(11, 11),
            List.of(BlockHeader.BYTES, BlockHeaderTest.LEAF.bytes().length),
            "Header size"
        );
    }

    @Test
    void decodesUnsignedFields() {
        Assertions.assertEquals(
            new BlockHeader(
                new RevisionNumber(RevisionNumber.MAX), 254, 0xFFFF, 0x8000,
                0xFFFD
            ),
            BlockHeader.parse(
                HexFormat.of().parseHex("fffffffffeffff8000fffd00")
            ),
            "Every field unsigned"
        );
    }

    @Test
    void roundTrips() {
        Assertions.assertEquals(
            BlockHeaderTest.LEAF,
            BlockHeader.parse(BlockHeaderTest.LEAF.bytes()),
            "Round trip"
        );
    }

    @Test
    void countsDirectoryEntries() {
        Assertions.assertEquals(
            List.of(2, 11, 13),
            List.of(
                BlockHeaderTest.LEAF.entries(),
                BlockHeaderTest.LEAF.entryOffset(0),
                BlockHeaderTest.LEAF.entryOffset(1)
            ),
            "Entries and offsets"
        );
    }

    @Test
    void rejectsBadDirectoryIndex() {
        for (final int index : new int[] {-1, 2}) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> BlockHeaderTest.LEAF.entryOffset(index),
                String.format("Index %d", index)
            );
        }
    }

    @Test
    void classifiesLevels() {
        final BlockHeader free = new BlockHeader(
            new RevisionNumber(2L), BlockHeader.LEVEL_FREELIST, 0, 0, 11
        );
        final BlockHeader branch = new BlockHeader(
            new RevisionNumber(2L), 1, 0, 0, 11
        );
        Assertions.assertEquals(
            List.of(true, false, false, true, false, false),
            List.of(
                BlockHeaderTest.LEAF.leaf(), BlockHeaderTest.LEAF.freelist(),
                free.leaf(), free.freelist(), branch.leaf(), branch.freelist()
            ),
            "Leaf and free-list flags"
        );
    }

    @Test
    void rejectsShortBlock() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> BlockHeader.parse(new byte[10]),
            "Ten bytes"
        );
    }

    @Test
    void acceptsExactHeader() {
        Assertions.assertEquals(
            0,
            BlockHeader.parse(HexFormat.of().parseHex("000000000000000000000b"))
                .entries(),
            "Empty directory"
        );
    }

    @Test
    void rejectsCorruptDirectoryEnd() {
        for (final String end : new String[] {"000a", "000c", "0000"}) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> BlockHeader.parse(
                    HexFormat.of().parseHex(
                        String.format("000000000000000000%s", end)
                    )
                ),
                end
            );
        }
    }

    @Test
    void rejectsOutOfRangeFields() {
        final RevisionNumber rev = new RevisionNumber(0L);
        final List<Runnable> bad = List.of(
            () -> new BlockHeader(rev, -1, 0, 0, 11),
            () -> new BlockHeader(rev, 256, 0, 0, 11),
            () -> new BlockHeader(rev, 0, -1, 0, 11),
            () -> new BlockHeader(rev, 0, 0x10000, 0, 11),
            () -> new BlockHeader(rev, 0, 0, -1, 11),
            () -> new BlockHeader(rev, 0, 0, 0x10000, 11),
            () -> new BlockHeader(rev, 0, 0, 0, 0x10001),
            () -> new BlockHeader(rev, 0, 0, 0, 12),
            () -> new BlockHeader(rev, 0, 0, 0, 9)
        );
        for (final Runnable item : bad) {
            Assertions.assertThrows(
                IllegalArgumentException.class, item::run, "Out of range"
            );
        }
    }

    @Test
    void acceptsBoundaryFields() {
        Assertions.assertEquals(
            255,
            new BlockHeader(new RevisionNumber(0L), 255, 0xFFFF, 0, 0xFFFF)
                .level(),
            "Upper bounds"
        );
    }
}
