/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Block}.
 *
 * @since 0.1.0
 */
final class BlockTest {

    /**
     * Block number of the tests.
     */
    private static final BlockNumber NUM = new BlockNumber(3L);

    @Test
    void readsRealHeader() throws IOException {
        final Block block = BlockTest.real("termlist");
        Assertions.assertEquals(
            List.of(new RevisionNumber(1L), 0, 113, BlockTest.NUM),
            List.of(
                block.revision(), block.level(), block.entries(),
                block.number()
            ),
            "Term list block"
        );
    }

    @Test
    void searchesRealLeaf() throws IOException {
        final Block block = BlockTest.real("termlist");
        final byte[] five = DocDataKeys.of(DocId.of(5)).data();
        final byte[] after = DocDataKeys.of(DocId.of(500)).data();
        Assertions.assertEquals(
            List.of(5, 0, 112, 0, 0),
            List.of(
                block.searchLeaf(five, five.length),
                block.searchLeaf(new byte[0], 0),
                block.searchLeaf(after, after.length),
                block.compareLeaf(5, five, five.length),
                block.searchLeaf(five, 1)
            ),
            "Positions of keys"
        );
    }

    @Test
    void searchesEveryRealKey() throws IOException {
        final Block block = BlockTest.real("termlist");
        for (int idx = 0; idx < block.entries(); ++idx) {
            final byte[] key = block.leafKey(idx).data();
            Assertions.assertEquals(
                idx, block.searchLeaf(key, key.length),
                String.format("Item %d", idx)
            );
        }
    }

    @Test
    void comparesComponents() {
        final ItemKey key = ItemKey.of(new byte[] {5});
        final Block block = BlockTest.leaf(
            LeafItem.root(),
            LeafItem.of(key, new TagPart(1, false, false), new byte[] {1}),
            LeafItem.of(key, new TagPart(2, false, true), new byte[] {2})
        );
        Assertions.assertEquals(
            List.of(0, -1, 1, 2, false, true, 1),
            List.of(
                block.compareLeaf(1, key.data(), 1),
                block.compareLeaf(2, key.data(), 1),
                block.searchLeaf(key.data(), 1),
                block.component(2), block.first(2), block.last(2),
                Integer.signum(block.compareLeaf(0, key.data(), 1))
            ),
            "First component matches the search"
        );
    }

    @Test
    void findsKeyBeforeEveryItem() {
        Assertions.assertEquals(
            -1,
            BlockTest.leaf(
                LeafItem.of(
                    ItemKey.of(new byte[] {5}), TagPart.WHOLE, new byte[0]
                )
            ).searchLeaf(new byte[] {4}, 1),
            "Before the first item"
        );
    }

    @Test
    void searchesBranch() {
        final Block block = BlockTest.block(
            1,
            BranchItem.nullKey(new BlockNumber(10L)),
            new BranchItem(ItemKey.of(new byte[] {5}), 1, new BlockNumber(11L)),
            new BranchItem(ItemKey.of(new byte[] {5}), 3, new BlockNumber(12L)),
            new BranchItem(ItemKey.of(new byte[] {9}), 1, new BlockNumber(13L))
        );
        Assertions.assertEquals(
            List.of(0, 1, 2, 3, 3, 0, new BlockNumber(12L), "05"),
            List.of(
                block.searchBranch(new byte[] {4}, 1),
                block.searchBranch(new byte[] {5}, 1),
                block.searchBranch(new byte[] {5, 0}, 2),
                block.searchBranch(new byte[] {9}, 1),
                block.searchBranch(new byte[] {(byte) 0xF0}, 1),
                block.searchBranch(new byte[0], 0),
                block.child(2), block.branchKey(2).toString()
            ),
            "Child holding each key"
        );
    }

    @Test
    void searchesBranchOfTwoItems() {
        final Block block = BlockTest.block(
            1,
            BranchItem.nullKey(new BlockNumber(10L)),
            new BranchItem(ItemKey.of(new byte[] {5}), 1, new BlockNumber(11L))
        );
        Assertions.assertEquals(
            List.of(0, 1, 1),
            List.of(
                block.searchBranch(new byte[] {4}, 1),
                block.searchBranch(new byte[] {5}, 1),
                block.searchBranch(new byte[] {6}, 1)
            ),
            "Two children"
        );
    }

    @Test
    void appendsAndFlagsChunks() throws IOException {
        final Block block = BlockTest.leaf(
            LeafItem.of(
                ItemKey.of(new byte[] {1}), new TagPart(3, true, false),
                new byte[] {7, 8}
            )
        );
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        block.appendChunk(0, out);
        Assertions.assertEquals(
            List.of(3, true, false, false, List.of((byte) 7, (byte) 8)),
            List.of(
                block.component(0), block.compressed(0), block.first(0),
                block.last(0),
                List.of(out.toByteArray()[0], out.toByteArray()[1])
            ),
            "Third component"
        );
    }

    @Test
    void buildsFakeRoot() {
        final Block block = Block.fakeRoot(BlockSize.MIN);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        block.appendChunk(0, out);
        Assertions.assertEquals(
            List.of(
                new BlockHeader(new RevisionNumber(0L), 0, 2032, 2032, 13),
                new BlockNumber(0L), ItemKey.EMPTY, 0, true, true, false
            ),
            List.of(
                block.header(), block.number(), block.leafKey(0), out.size(),
                block.first(0), block.last(0), block.compressed(0)
            ),
            "Empty table root"
        );
    }

    @Test
    void rejectsDirectoryEndOutsideBlock() {
        for (final int end : new int[] {0, 10, 2049, 0xFFFF}) {
            final byte[] data = new byte[BlockSize.MIN.bytes()];
            ByteBuffer.wrap(data).putShort(9, (short) end);
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> Block.parse(BlockTest.NUM, data),
                String.format("Directory end %d", end)
            );
        }
    }

    @Test
    void acceptsDirectoryEndAtBounds() {
        for (final int end : new int[] {11, 2048}) {
            final byte[] data = new byte[BlockSize.MIN.bytes()];
            ByteBuffer.wrap(data).putShort(9, (short) end);
            Assertions.assertEquals(
                (end - 11) / 2, Block.parse(BlockTest.NUM, data).entries(),
                String.format("Directory end %d", end)
            );
        }
    }

    @Test
    void skipsDirectoryCheckOfFreeListBlock() {
        final byte[] data = new byte[BlockSize.MIN.bytes()];
        data[4] = (byte) BlockHeader.LEVEL_FREELIST;
        Assertions.assertEquals(
            BlockHeader.LEVEL_FREELIST,
            Block.parse(BlockTest.NUM, data).level(),
            "Free-list block"
        );
    }

    @Test
    void rejectsBadIndex() {
        final Block block = BlockTest.leaf(LeafItem.root());
        for (final int idx : new int[] {-1, 1}) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> block.leafKey(idx),
                String.format("Index %d", idx)
            );
        }
    }

    @Test
    void rejectsItemsOverrunningBlock() {
        final byte[] data = Trees.block(
            0, List.of(LeafItem.root()), BlockSize.MIN
        );
        final ByteBuffer buf = ByteBuffer.wrap(data);
        buf.putShort(11, (short) 2046);
        final Block head = Block.parse(BlockTest.NUM, data.clone());
        buf.putShort(11, (short) 2044);
        data[2046] = 5;
        final Block key = Block.parse(BlockTest.NUM, data.clone());
        data[2046] = 0;
        data[2044] = 0x3F;
        final Block size = Block.parse(BlockTest.NUM, data.clone());
        data[2044] = 0x20;
        data[2045] = 0;
        data[2046] = 1;
        final Block tag = Block.parse(BlockTest.NUM, data.clone());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class, () -> head.leafKey(0), "Header"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class, () -> key.leafKey(0), "Key"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class, () -> size.appendChunk(0, out),
                "Size"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class, () -> tag.appendChunk(0, out),
                "Key longer than item"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> BlockTest.block(1, BranchItem.nullKey(BlockTest.NUM))
                    .child(1),
                "Branch index"
            )
        );
    }

    @Test
    void rejectsMissingCount() {
        final byte[] data = Trees.block(
            0, List.of(LeafItem.root()), BlockSize.MIN
        );
        data[2045] = 0;
        final Block block = Block.parse(BlockTest.NUM, data);
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> block.component(0),
            "Count past the block end"
        );
    }

    @Test
    void describesItself() {
        Assertions.assertEquals(
            "Block[number=0, level=0, entries=1]",
            Block.fakeRoot(BlockSize.MIN).toString(), "Description"
        );
    }

    private static Block leaf(final LeafItem... items) {
        return BlockTest.block(0, items);
    }

    private static Block block(final int level, final TableItem... items) {
        return Block.parse(
            BlockTest.NUM, Trees.block(level, List.of(items), BlockSize.MIN)
        );
    }

    private static Block real(final String table) throws IOException {
        final InputStream input = BlockTest.class.getResourceAsStream(
            String.format("oversized/%s.bin", table)
        );
        try (input) {
            return Block.parse(BlockTest.NUM, input.readAllBytes());
        }
    }
}
