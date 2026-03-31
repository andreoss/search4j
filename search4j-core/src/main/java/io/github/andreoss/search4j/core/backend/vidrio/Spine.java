/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.util.Arrays;

/**
 * Path of blocks being changed, from the root of a tree being written down
 * to a leaf item.
 *
 * <p>Each level keeps its block bytes, number, directory offset and
 * whether the block must be written back; a block leaving the path is
 * written back then, the others on {@link #flush()}. A block first changed
 * in a revision moves to a new block number (copy on write).</p>
 *
 * @since 0.1.0
 */
final class Spine {

    /**
     * Block number of no block.
     */
    static final long NONE = -1L;

    /**
     * Store of the blocks.
     */
    private final BlockStore store;

    /**
     * Free list handing out block numbers.
     */
    private final FreeBlocks free;

    /**
     * Block bytes at each level.
     */
    private final Page[] pages;

    /**
     * Block number at each level.
     */
    private final long[] numbers;

    /**
     * Directory offset at each level.
     */
    private final int[] pos;

    /**
     * Whether the block at each level must be written back.
     */
    private final boolean[] dirty;

    /**
     * Level of the root.
     */
    private int top;

    /**
     * Revision open; blocks written carry the next one.
     */
    private long revision;

    /**
     * Creates an empty path.
     *
     * @param store Store of the blocks
     * @param free Free list
     */
    Spine(final BlockStore store, final FreeBlocks free) {
        this.store = store;
        this.free = free;
        this.pages = new Page[VidrioFormat.CURSOR_LEVELS];
        this.numbers = new long[VidrioFormat.CURSOR_LEVELS];
        this.pos = new int[VidrioFormat.CURSOR_LEVELS];
        this.dirty = new boolean[VidrioFormat.CURSOR_LEVELS];
    }

    /**
     * Drops the path and starts again from a root at its revision.
     *
     * @param root Root of the tree
     */
    void open(final TableRoot root) {
        Arrays.fill(this.pages, null);
        Arrays.fill(this.numbers, Spine.NONE);
        Arrays.fill(this.pos, -1);
        Arrays.fill(this.dirty, false);
        this.top = root.level();
        this.revision = root.revision().value();
        if (root.fake()) {
            this.pages[0] = Page.fakeRoot(
                this.store.size().bytes(), this.revision + 1L
            );
            this.numbers[0] = this.free.take();
            this.dirty[0] = true;
        } else {
            this.place(this.top, root.block().value());
            if (this.pages[this.top].revision() > this.revision) {
                throw new DatabaseCorruptError("Block overwritten");
            }
        }
    }

    /**
     * Level of the root.
     *
     * @return Level, 0 when the root is a leaf
     */
    int level() {
        return this.top;
    }

    /**
     * Revision open.
     *
     * @return Unsigned 32-bit revision
     */
    long revision() {
        return this.revision;
    }

    /**
     * Block at a level.
     *
     * @param level Level from 0 at the leaves
     * @return Block bytes at the level
     */
    Page page(final int level) {
        return this.pages[level];
    }

    /**
     * Block number at a level.
     *
     * @param level Level from 0 at the leaves
     * @return Block number, {@link #NONE} when freed
     */
    long number(final int level) {
        return this.numbers[level];
    }

    /**
     * Moves the block at a level to another block number.
     *
     * @param level Level from 0 at the leaves
     * @param block Block number
     */
    void number(final int level, final long block) {
        this.numbers[level] = block;
    }

    /**
     * Directory offset at a level.
     *
     * @param level Level from 0 at the leaves
     * @return Directory offset
     */
    int position(final int level) {
        return this.pos[level];
    }

    /**
     * Sets the directory offset at a level.
     *
     * @param level Level from 0 at the leaves
     * @param offset Directory offset
     */
    void position(final int level, final int offset) {
        this.pos[level] = offset;
    }

    /**
     * Sets whether the block at a level must be written back.
     *
     * @param level Level from 0 at the leaves
     * @param flag True to write it back
     */
    void dirty(final int level, final boolean flag) {
        this.dirty[level] = flag;
    }

    /**
     * Frees the block at a level, which is then not written back.
     *
     * @param level Level from 0 at the leaves
     */
    void release(final int level) {
        this.free.mark(this.numbers[level]);
        this.dirty[level] = false;
        this.numbers[level] = Spine.NONE;
    }

    /**
     * Adds a root level above the current root.
     *
     * @param root Empty branch block of the new level
     * @param block Its block number
     */
    void grow(final Page root, final long block) {
        this.top += 1;
        if (this.top == VidrioFormat.CURSOR_LEVELS) {
            throw new DatabaseCorruptError(
                String.format(
                    "Tree has grown impossibly large (%d levels)",
                    VidrioFormat.CURSOR_LEVELS
                )
            );
        }
        this.pages[this.top] = root;
        this.numbers[this.top] = block;
        this.pos[this.top] = Page.START;
        this.dirty[this.top] = true;
    }

    /**
     * Removes the root level, whose single child becomes the root.
     */
    void shrink() {
        final long child = this.pages[this.top].child(Page.START);
        this.release(this.top);
        this.top -= 1;
        this.place(this.top, child);
    }

    /**
     * Writes a block that is not on the path.
     *
     * @param block Block number
     * @param page Block bytes
     */
    void write(final long block, final Page page) {
        page.store(this.store, block);
    }

    /**
     * Descends to the last leaf item at or before a draft item.
     *
     * @param draft Item sought
     * @return True if an item with its key and component is present
     */
    boolean find(final Draft draft) {
        for (int level = this.top; level > 0; --level) {
            final int offset = this.pages[level].findBranch(draft);
            this.pos[level] = offset;
            this.place(level - 1, this.pages[level].child(offset));
        }
        final int offset = this.pages[0].findLeaf(draft);
        this.pos[0] = offset;
        return offset >= Page.START && this.pages[0].order(offset, draft) == 0;
    }

    /**
     * Prepares the path for a change of its leaf: each block not yet
     * written in this revision moves to a new block number, its old one
     * freed, and its parent is updated, up to the first block already
     * changed.
     */
    void alter() {
        final long next = this.revision + 1L;
        int level = 0;
        boolean more = true;
        while (more && !this.dirty[level]) {
            this.dirty[level] = true;
            more = this.pages[level].revision() != next;
            if (more) {
                this.free.mark(this.numbers[level]);
                this.pages[level].revision(next);
                final long block = this.free.take();
                this.numbers[level] = block;
                more = level < this.top;
                if (more) {
                    level += 1;
                    this.pages[level].child(this.pos[level], block);
                }
            }
        }
    }

    /**
     * Writes back every block of the path that must be.
     */
    void flush() {
        for (int level = this.top; level >= 0; --level) {
            if (this.dirty[level]) {
                this.write(this.numbers[level], this.pages[level]);
            }
        }
    }

    /**
     * Root block of the tree as changed so far, every changed block
     * written first.
     *
     * @return Root block
     */
    Block root() {
        this.flush();
        return Block.parse(
            new BlockNumber(this.numbers[this.top]),
            this.pages[this.top].bytes()
        );
    }

    private void place(final int level, final long block) {
        if (block != this.numbers[level]) {
            if (this.dirty[level]) {
                this.write(this.numbers[level], this.pages[level]);
                this.dirty[level] = false;
            }
            final Page page = Page.of(this.store.read(new BlockNumber(block)));
            if (level < this.top
                && page.revision() > this.pages[level + 1].revision()) {
                throw new DatabaseCorruptError("Block overwritten");
            }
            if (page.level() != level) {
                throw new DatabaseCorruptError(
                    String.format(
                        "Expected block %d to be level %d, not %d",
                        block, level, page.level()
                    )
                );
            }
            this.pages[level] = page;
            this.numbers[level] = block;
        }
    }
}
