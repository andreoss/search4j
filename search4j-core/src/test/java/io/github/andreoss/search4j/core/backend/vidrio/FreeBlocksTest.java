/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link FreeBlocks}.
 *
 * @since 0.1.0
 */
final class FreeBlocksTest {

    /**
     * Offset of the next-block pointer of a minimum-size free-list block.
     */
    private static final int LAST = 2044;

    @Test
    void handsOutUnusedBlocksWhenEmpty() {
        final FreeBlocks free = FreeBlocksTest.fresh();
        Assertions.assertEquals(
            List.of(0L, 1L, 2L, 3L),
            List.of(free.take(), free.take(), free.take(), free.unused()),
            "Blocks from the end of the file"
        );
    }

    @Test
    void reusesFreedBlocksAfterCommit() {
        final FreeBlocks free = FreeBlocksTest.fresh();
        for (int idx = 0; idx < 5; ++idx) {
            free.take();
        }
        free.mark(1L);
        free.mark(3L);
        final long during = free.take();
        free.commit(1L);
        Assertions.assertEquals(
            List.of(
                6L, 1L, 3L, 7L,
                new FreeListInfo(
                    new RevisionNumber(1L), new BlockNumber(8L),
                    FreeBlocksTest.mark(5L, 16), FreeBlocksTest.mark(5L, 16)
                )
            ),
            List.of(during, free.take(), free.take(), free.take(), free.info()),
            "Freed blocks handed out from the next revision"
        );
    }

    @Test
    void writesFreeListBlock() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final FreeBlocks free = FreeBlocks.of(blocks, FreeListInfo.EMPTY);
        free.mark(9L);
        free.commit(4L);
        final ByteBuffer block = ByteBuffer.wrap(blocks.bytes(0L));
        Assertions.assertEquals(
            List.of(4, 0xFE000000, 9, -1, -1),
            List.of(
                block.getInt(0), block.getInt(4), block.getInt(8),
                block.getInt(12), block.getInt(FreeBlocksTest.LAST)
            ),
            "Revision, level, entry, unused fill, no next block"
        );
    }

    @Test
    void hopsToNextFreeListBlock() {
        final FreeBlocks free = FreeBlocksTest.fresh();
        for (int idx = 0; idx < 1000; ++idx) {
            free.take();
        }
        for (long blk = 0L; blk < 600L; ++blk) {
            free.mark(blk);
        }
        free.commit(1L);
        final List<Long> taken = new ArrayList<>(600);
        for (int idx = 0; idx < 600; ++idx) {
            taken.add(free.take());
        }
        final long after = free.take();
        free.commit(2L);
        Assertions.assertEquals(
            List.of(LongStream.range(0L, 600L).boxed().toList(), 1002L, 1000L),
            List.of(taken, after, free.take()),
            "Entries in order across two blocks, the first freed"
        );
    }

    @Test
    void continuesCommittedTailBlock() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final FreeBlocks first = FreeBlocks.of(blocks, FreeListInfo.EMPTY);
        first.take();
        first.mark(0L);
        first.commit(1L);
        final FreeBlocks second = FreeBlocks.of(blocks, first.info());
        second.mark(7L);
        second.commit(2L);
        final FreeBlocks third = FreeBlocks.of(blocks, second.info());
        Assertions.assertEquals(
            List.of(0L, 7L, 2L),
            List.of(third.take(), third.take(), third.take()),
            "Entries of both revisions kept"
        );
    }

    @Test
    void forgetsUncommittedEntriesOnRestore() {
        final FreeBlocks free = FreeBlocksTest.fresh();
        free.take();
        free.mark(0L);
        free.commit(1L);
        final FreeListInfo info = free.info();
        for (long blk = 10L; blk < 600L; ++blk) {
            free.mark(blk);
        }
        free.restore(info);
        free.mark(5L);
        free.commit(2L);
        Assertions.assertEquals(
            List.of(0L, 5L, 2L),
            List.of(free.take(), free.take(), free.take()),
            "Only committed entries and those after the restore"
        );
    }

    @Test
    void rejectsHeadBlockOfTree() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        blocks.write(new BlockNumber(0L), Block.fakeRoot(BlockSize.MIN).copy());
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> FreeBlocksTest.listed(blocks, 0L, 12).take(),
            "Freelist corrupt"
        );
    }

    @Test
    void rejectsMissingHeadBlock() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            FreeBlocks.of(
                new MemoryBlocks(new HashMap<>(), BlockSize.MIN),
                new FreeListInfo(
                    new RevisionNumber(1L), new BlockNumber(1L),
                    FreeBlocksTest.mark(BlockNumber.MAX, 8),
                    FreeBlocksTest.mark(0L, 8)
                )
            )::take,
            "Freelist pointer invalid"
        );
    }

    @Test
    void rejectsUnusedEntry() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        blocks.write(new BlockNumber(0L), FreeBlocksTest.chain(8, -1));
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> FreeBlocksTest.listed(blocks, 0L, 12).take(),
            "Ran off end of freelist"
        );
    }

    @Test
    void rejectsMissingNextBlock() {
        final MemoryBlocks blocks = new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        blocks.write(
            new BlockNumber(0L), FreeBlocksTest.chain(FreeBlocksTest.LAST, -1)
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            FreeBlocks.of(
                blocks,
                new FreeListInfo(
                    new RevisionNumber(1L), new BlockNumber(2L),
                    FreeBlocksTest.mark(0L, FreeBlocksTest.LAST),
                    FreeBlocksTest.mark(1L, 8)
                )
            )::take,
            "Freelist next pointer invalid"
        );
    }

    @Test
    void freesChainBlockConsumedWhileAppending() {
        final FreeBlocks free = FreeBlocksTest.fresh();
        for (int idx = 0; idx < 1000; ++idx) {
            free.take();
        }
        for (long blk = 0L; blk < 510L; ++blk) {
            free.mark(blk);
        }
        free.commit(1L);
        for (int idx = 0; idx < 509; ++idx) {
            free.take();
        }
        for (long blk = 2000L; blk < 2509L; ++blk) {
            free.mark(blk);
        }
        free.commit(2L);
        final List<Long> taken = new ArrayList<>(512);
        for (int idx = 0; idx < 512; ++idx) {
            taken.add(free.take());
        }
        free.commit(3L);
        final List<Long> expected = new ArrayList<>(512);
        expected.addAll(LongStream.range(2000L, 2509L).boxed().toList());
        expected.addAll(List.of(1000L, 1002L, 1003L));
        Assertions.assertEquals(
            List.of(expected, 1001L),
            List.of(taken, free.take()),
            "Head block consumed during an append freed after the entry"
        );
    }

    private static FreeBlocks listed(
        final MemoryBlocks blocks, final long head, final int end
    ) {
        return FreeBlocks.of(
            blocks,
            new FreeListInfo(
                new RevisionNumber(1L), new BlockNumber(1L),
                FreeBlocksTest.mark(head, 8), FreeBlocksTest.mark(head, end)
            )
        );
    }

    private static byte[] chain(final int offset, final int value) {
        final ByteBuffer block = ByteBuffer.allocate(BlockSize.MIN.bytes());
        block.put(4, (byte) BlockHeader.LEVEL_FREELIST);
        block.putInt(offset, value);
        return block.array();
    }

    private static FreeListMark mark(final long block, final int offset) {
        return new FreeListMark(new BlockNumber(block), offset);
    }

    private static FreeBlocks fresh() {
        return FreeBlocks.of(
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN), FreeListInfo.EMPTY
        );
    }
}
