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
import io.github.andreoss.search4j.core.error.DatabaseModifiedError;
import io.github.andreoss.search4j.core.ser.ZlibChunks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Path of blocks from the root of a tree down to a leaf item.
 *
 * <p>Each level keeps its block and directory index; a block already on
 * the path is not read again.</p>
 *
 * @since 0.1.0
 */
public final class TreePath {

    /**
     * Block on the path at each level, the root last.
     */
    private final Block[] path;

    /**
     * Number of the block held at each level, -1 when none.
     */
    private final long[] numbers;

    /**
     * Directory index at each level, -1 before the first leaf item.
     */
    private final int[] pos;

    /**
     * Source of the blocks.
     */
    private final Blocks blocks;

    /**
     * Creates a path holding only the root block.
     *
     * @param blocks Source of the blocks
     * @param top Root block, at the top level of the tree
     */
    public TreePath(final Blocks blocks, final Block top) {
        this(
            TreePath.levelsOf(top), TreePath.numbersOf(top),
            new int[top.level() + 1], blocks
        );
    }

    /**
     * Creates a path over its state.
     *
     * @param path Blocks per level
     * @param numbers Block numbers per level
     * @param pos Directory index per level
     * @param blocks Source of the blocks
     */
    private TreePath(
        final Block[] path, final long[] numbers, final int[] pos,
        final Blocks blocks
    ) {
        this.path = path;
        this.numbers = numbers;
        this.pos = pos;
        this.blocks = blocks;
    }

    /**
     * Descends to the last leaf item at or before a key's first component.
     *
     * <p>A key longer than {@link ItemKey#MAX_LENGTH} is searched in its
     * truncated form and never found.</p>
     *
     * @param key Key bytes
     * @return True if the key itself was found
     */
    public boolean find(final byte[] key) {
        final int len = Math.min(key.length, ItemKey.MAX_LENGTH);
        for (int level = this.path.length - 1; level > 0; --level) {
            this.pos[level] = this.path[level].searchBranch(key, len);
            this.descend(level);
        }
        final int idx = this.path[0].searchLeaf(key, len);
        this.pos[0] = idx;
        return key.length == len && idx >= 0
            && this.path[0].compareLeaf(idx, key, len) == 0;
    }

    /**
     * Tests whether the leaf position is before the leaf's first item.
     *
     * @return True before the first item
     */
    public boolean before() {
        return this.pos[0] < 0;
    }

    /**
     * Moves the leaf position onto the leaf's first item.
     */
    public void clamp() {
        this.pos[0] = 0;
    }

    /**
     * Moves to the next leaf item.
     *
     * @return False at the last item of the tree, the position kept
     */
    public boolean next() {
        return this.nextAt(0);
    }

    /**
     * Moves to the previous leaf item.
     *
     * @return False at the first item of the tree, the position kept
     */
    public boolean prev() {
        return this.prevAt(0);
    }

    /**
     * Key of the current leaf item.
     *
     * @return Key of the item
     */
    public ItemKey key() {
        return this.path[0].leafKey(this.pos[0]);
    }

    /**
     * Tests whether the current leaf item is the first component of a tag.
     *
     * @return True for component 1
     */
    public boolean first() {
        return this.path[0].first(this.pos[0]);
    }

    /**
     * Reads the tag starting at the current item, inflating it if needed.
     *
     * <p>The path ends on the tag's last component.</p>
     *
     * @return Tag bytes
     */
    public byte[] tag() {
        final byte[] tag;
        if (this.path[0].compressed(this.pos[0])) {
            tag = this.inflated();
        } else {
            tag = this.plain();
        }
        return tag;
    }

    private byte[] plain() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean last = this.path[0].last(this.pos[0]);
        this.path[0].appendChunk(this.pos[0], out);
        while (!last) {
            this.continuation();
            last = this.path[0].last(this.pos[0]);
            this.path[0].appendChunk(this.pos[0], out);
        }
        return out.toByteArray();
    }

    private byte[] inflated() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZlibChunks inflater = new ZlibChunks()) {
            boolean last = this.inflate(inflater, out);
            while (!last) {
                this.continuation();
                last = this.inflate(inflater, out);
            }
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                "inflate failed", ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
        return out.toByteArray();
    }

    private boolean inflate(
        final ZlibChunks inflater, final ByteArrayOutputStream out
    ) throws IOException {
        final boolean last = this.path[0].last(this.pos[0]);
        final boolean done =
            this.path[0].inflateChunk(this.pos[0], inflater, out);
        if (done && !last) {
            throw new DatabaseCorruptError(
                "Too many chunks of compressed data"
            );
        }
        if (!done && last) {
            throw new DatabaseCorruptError("Too few chunks of compressed data");
        }
        return last;
    }

    private void continuation() {
        if (!this.nextAt(0)) {
            throw new DatabaseCorruptError(
                "Unexpected end of table when reading continuation of tag"
            );
        }
    }

    private boolean nextAt(final int level) {
        boolean moved = true;
        int idx = this.pos[level] + 1;
        if (idx >= this.path[level].entries()) {
            moved = level + 1 < this.path.length && this.nextAt(level + 1);
            idx = 0;
        }
        if (moved) {
            this.pos[level] = idx;
            this.descend(level);
        }
        return moved;
    }

    private boolean prevAt(final int level) {
        boolean moved = true;
        int idx = this.pos[level];
        if (idx <= 0) {
            moved = level + 1 < this.path.length && this.prevAt(level + 1);
            idx = this.path[level].entries();
        }
        if (moved) {
            this.pos[level] = idx - 1;
            this.descend(level);
        }
        return moved;
    }

    private void descend(final int level) {
        if (level > 0) {
            final BlockNumber child =
                this.path[level].child(this.pos[level]);
            if (this.numbers[level - 1] != child.value()) {
                this.place(level - 1, child);
            }
        }
    }

    private void place(final int level, final BlockNumber number) {
        final Block block = this.blocks.read(number);
        if (block.revision().compareTo(this.path[level + 1].revision()) > 0) {
            throw new DatabaseModifiedError(
                "The revision being read has been discarded"
            );
        }
        if (block.level() != level) {
            throw new DatabaseCorruptError(
                String.format(
                    "Expected block %s to be level %d, not %d",
                    number, level, block.level()
                )
            );
        }
        this.path[level] = block;
        this.numbers[level] = number.value();
    }

    private static Block[] levelsOf(final Block top) {
        final Block[] levels = new Block[top.level() + 1];
        levels[top.level()] = top;
        return levels;
    }

    private static long[] numbersOf(final Block top) {
        final long[] held = new long[top.level() + 1];
        Arrays.fill(held, -1L);
        held[top.level()] = top.number().value();
        return held;
    }
}
