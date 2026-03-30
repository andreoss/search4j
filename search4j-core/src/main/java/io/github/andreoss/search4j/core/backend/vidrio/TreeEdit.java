/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.nio.ByteBuffer;

/**
 * Changes to the items of a tree on a path: adding with block splits and
 * root growth, deleting with freeing of emptied blocks and root shrinking.
 *
 * <p>Items added one after another at the same place switch to sequential
 * mode, where a full block splits at the insert point instead of its
 * middle.</p>
 *
 * @since 0.1.0
 */
final class TreeEdit {

    /**
     * Sequence count of a change out of order.
     */
    private static final int SEQ_START = -10;

    /**
     * Path being changed.
     */
    private final Spine spine;

    /**
     * Free list handing out block numbers.
     */
    private final FreeBlocks free;

    /**
     * Buffer of the block size for compaction.
     */
    private final byte[] scratch;

    /**
     * Additions in sequence so far, from {@link #SEQ_START} up to 0.
     */
    private int seq;

    /**
     * Block that the last addition changed.
     */
    private long changed;

    /**
     * Directory offset of the last addition.
     */
    private int offset;

    /**
     * Whether every change so far was an addition in key order.
     */
    private boolean ordered;

    /**
     * Creates an editor.
     *
     * @param spine Path being changed
     * @param free Free list
     * @param size Block size
     */
    TreeEdit(final Spine spine, final FreeBlocks free, final int size) {
        this.spine = spine;
        this.free = free;
        this.scratch = new byte[size];
        this.seq = TreeEdit.SEQ_START;
        this.offset = Page.START;
        this.ordered = true;
    }

    /**
     * Starts a revision: no addition seen yet.
     *
     * @param sequential Whether every change so far was in key order
     */
    void rewind(final boolean sequential) {
        this.changed = 0L;
        this.offset = Page.START;
        this.seq = TreeEdit.SEQ_START;
        this.ordered = sequential;
    }

    /**
     * Records a change out of key order.
     */
    void restart() {
        this.seq = TreeEdit.SEQ_START;
        this.ordered = false;
    }

    /**
     * Sets sequential mode, splitting full blocks at the insert point.
     */
    void compaction() {
        this.seq = 0;
    }

    /**
     * Whether every change so far was an addition in key order.
     *
     * @return True while additions came in order
     */
    boolean sequential() {
        return this.ordered;
    }

    /**
     * Records an addition at the leaf position, before it is added.
     */
    void follow() {
        if (this.changed == this.spine.number(0)
            && this.offset == this.spine.position(0)) {
            if (this.seq < 0) {
                this.seq += 1;
            }
        } else {
            this.restart();
        }
    }

    /**
     * Adds an item at the leaf position, splitting the leaf if full.
     *
     * @param draft Item
     */
    void addLeaf(final Draft draft) {
        final Page page = this.spine.page(0);
        int pos = this.spine.position(0);
        final int needed = draft.size() + Page.ENTRY;
        final long block;
        if (page.totalFree() < needed) {
            final Halves halves = this.divide(0);
            if (this.upper(halves, pos, needed)) {
                pos -= halves.mid() - Page.START;
                page.insert(draft.item(), pos);
                block = this.spine.number(0);
            } else {
                halves.lower().insert(draft.item(), pos);
                block = halves.number();
            }
            this.spine.write(halves.number(), halves.lower());
            if (this.spine.level() == 0) {
                this.splitRoot(halves.number());
            }
            this.enterLeaf(halves.lower(), page);
        } else {
            this.room(page, needed);
            page.insert(draft.item(), pos);
            block = this.spine.number(0);
        }
        this.changed = block;
        this.offset = pos;
    }

    /**
     * Deletes the item at the leaf position.
     *
     * @param repeatedly Whether to free an emptied leaf and delete its
     *  parent item, repeating up the tree
     */
    void deleteLeaf(final boolean repeatedly) {
        final Page page = this.spine.page(0);
        page.remove(this.spine.position(0));
        if (repeatedly && this.spine.level() > 0
            && page.dirEnd() == Page.START) {
            this.spine.release(0);
            this.spine.dirty(1, true);
            this.deleteBranch(1);
        }
    }

    private void addBranch(final byte[] item, final int level) {
        final Page page = this.spine.page(level);
        int pos = this.spine.position(level);
        final int needed = item.length + Page.ENTRY;
        if (page.totalFree() < needed) {
            final Halves halves = this.divide(level);
            if (this.upper(halves, pos, needed)) {
                pos -= halves.mid() - Page.START;
                page.insert(item, pos);
            } else {
                halves.lower().insert(item, pos);
            }
            this.spine.write(halves.number(), halves.lower());
            if (this.spine.level() == level) {
                this.splitRoot(halves.number());
            }
            this.enter(
                level + 1,
                TreeEdit.separator(
                    this.spine.number(level), page.key(Page.START),
                    page.component(Page.START)
                )
            );
            page.nullKey(Page.START);
        } else {
            this.room(page, needed);
            page.insert(item, pos);
        }
    }

    private void deleteBranch(final int level) {
        final Page page = this.spine.page(level);
        page.remove(this.spine.position(level));
        if (level < this.spine.level()) {
            if (page.dirEnd() == Page.START) {
                this.spine.release(level);
                this.spine.dirty(level + 1, true);
                this.deleteBranch(level + 1);
            }
        } else {
            while (this.spine.level() > 0
                && this.spine.page(this.spine.level()).dirEnd()
                == Page.START + Page.ENTRY) {
                this.spine.shrink();
            }
        }
    }

    private Halves divide(final int level) {
        final Page page = this.spine.page(level);
        final int mid;
        if (this.seq < 0) {
            mid = page.midPoint();
        } else {
            mid = this.spine.position(level);
        }
        final long lower = this.spine.number(level);
        this.spine.number(level, this.free.take());
        final Page split = page.copy();
        split.keepBefore(mid);
        split.compact(this.scratch);
        page.keepFrom(mid);
        page.compact(this.scratch);
        return new Halves(split, lower, mid);
    }

    private boolean upper(final Halves halves, final int pos, final int needed) {
        final boolean upper;
        if (this.seq < 0) {
            upper = pos >= halves.mid();
        } else {
            upper = halves.lower().totalFree() < needed;
        }
        return upper;
    }

    private void room(final Page page, final int needed) {
        if (page.maxFree() < needed) {
            page.compact(this.scratch);
        }
    }

    private void splitRoot(final long child) {
        this.spine.grow(
            Page.fresh(
                this.scratch.length, this.spine.revision() + 1L,
                this.spine.level() + 1
            ),
            this.free.take()
        );
        this.addBranch(
            TreeEdit.separator(child, new byte[0], 0), this.spine.level()
        );
    }

    private void enterLeaf(final Page lower, final Page upper) {
        final byte[] prev = lower.key(lower.dirEnd() - Page.ENTRY);
        final byte[] key = upper.key(Page.START);
        final int min = Math.min(prev.length, key.length);
        int len = 0;
        while (len < min && prev[len] == key[len]) {
            len += 1;
        }
        if (len < key.length) {
            len += 1;
        }
        final byte[] cut = new byte[len];
        System.arraycopy(key, 0, cut, 0, len);
        this.enter(
            1,
            TreeEdit.separator(
                this.spine.number(0), cut, upper.component(Page.START)
            )
        );
    }

    private void enter(final int level, final byte[] item) {
        this.spine.position(level, this.spine.position(level) + Page.ENTRY);
        this.spine.dirty(level, true);
        this.addBranch(item, level);
    }

    private static byte[] separator(
        final long child, final byte[] key, final int component
    ) {
        final ByteBuffer item = ByteBuffer.allocate(
            BlockNumber.BYTES + ItemKey.LENGTH_BYTES + key.length
                + TableItem.COUNT_BYTES
        );
        item.putInt((int) child);
        item.put((byte) key.length);
        item.put(key);
        item.putShort((short) component);
        return item.array();
    }

    /**
     * Lower half of a split block.
     *
     * @param lower Lower half, not on the path
     * @param number Block number of the lower half
     * @param mid Directory offset where the block was split
     * @since 0.1.0
     */
    private record Halves(Page lower, long number, int mid) {
    }
}
