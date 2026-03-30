/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Accounting of the blocks of a committed table: every block below the
 * first unused one is in the tree or on the free list, exactly once.
 *
 * @since 0.1.0
 */
final class Audit {

    /**
     * Blocks of the table.
     */
    private final MemoryBlocks blocks;

    /**
     * Root information committed.
     */
    private final TableState state;

    /**
     * Creates an audit.
     *
     * @param blocks Blocks of the table
     * @param state Root information committed
     */
    Audit(final MemoryBlocks blocks, final TableState state) {
        this.blocks = blocks;
        this.state = state;
    }

    /**
     * Blocks not seen exactly once, or seen past the first unused block.
     *
     * @return Descriptions of the faults, empty when none
     */
    List<String> faults() {
        final List<String> faults = new ArrayList<>(0);
        final int[] seen = new int[(int) this.state.free().unused().value()];
        if (!this.state.root().fake()) {
            this.tree(this.state.root().block().value(), seen, faults);
        }
        this.chain(seen, faults);
        for (int num = 0; num < seen.length; ++num) {
            if (seen[num] != 1) {
                faults.add(String.format("block %d seen %d", num, seen[num]));
            }
        }
        return faults;
    }

    private void tree(
        final long number, final int[] seen, final List<String> faults
    ) {
        Audit.mark(number, seen, faults);
        final Block block = Block.parse(
            new BlockNumber(number), this.blocks.bytes(number)
        );
        if (block.level() > 0) {
            for (int idx = 0; idx < block.entries(); ++idx) {
                this.tree(block.child(idx).value(), seen, faults);
            }
        }
    }

    private void chain(final int[] seen, final List<String> faults) {
        final FreeListMark tail = this.state.free().tail();
        long blk = this.state.free().head().block().value();
        int pos = this.state.free().head().offset();
        if (blk != tail.block().value() || pos != tail.offset()) {
            Audit.mark(blk, seen, faults);
        }
        final int last = this.state.root().size().bytes() - Integer.BYTES;
        while (blk != tail.block().value() || pos != tail.offset()) {
            final long entry = Integer.toUnsignedLong(
                ByteBuffer.wrap(this.blocks.bytes(blk)).getInt(pos)
            );
            if (pos == last) {
                blk = entry;
                pos = 8;
            } else {
                pos += Integer.BYTES;
            }
            Audit.mark(entry, seen, faults);
        }
    }

    private static void mark(
        final long number, final int[] seen, final List<String> faults
    ) {
        if (number < seen.length) {
            seen[(int) number] += 1;
        } else {
            faults.add(String.format("block %d past the end", number));
        }
    }
}
