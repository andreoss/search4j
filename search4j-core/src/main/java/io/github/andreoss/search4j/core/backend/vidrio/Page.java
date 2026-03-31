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
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Bytes of a block being changed: header, item directory and items.
 *
 * <p>Positions are directory offsets from {@link #START}, two bytes per
 * entry, as in the stored block.</p>
 *
 * @since 0.1.0
 */
final class Page {

    /**
     * Offset of the first directory entry.
     */
    static final int START = BlockHeader.BYTES;

    /**
     * Bytes of a directory entry.
     */
    static final int ENTRY = BlockHeader.ENTRY_BYTES;

    /**
     * Offset of the level byte.
     */
    private static final int LEVEL_AT = 4;

    /**
     * Offset of the largest free run.
     */
    private static final int MAX_AT = 5;

    /**
     * Offset of the total free space.
     */
    private static final int TOTAL_AT = 7;

    /**
     * Offset of the directory end.
     */
    private static final int END_AT = 9;

    /**
     * Block bytes.
     */
    private final byte[] data;

    /**
     * Big-endian view of the bytes.
     */
    private final ByteBuffer view;

    /**
     * Creates a page over block bytes.
     *
     * @param data Block bytes, not copied
     * @param view Big-endian view of the same bytes
     */
    private Page(final byte[] data, final ByteBuffer view) {
        this.data = data;
        this.view = view;
    }

    /**
     * A page over a copy of a block read.
     *
     * @param block Block copied
     * @return Page over copied bytes
     */
    static Page of(final Block block) {
        return Page.over(block.copy());
    }

    /**
     * An empty block of a level, every byte past the header zero.
     *
     * @param size Block size
     * @param revision Revision writing the block
     * @param level Level of the block
     * @return Page over new bytes
     */
    static Page fresh(final int size, final long revision, final int level) {
        final Page page = Page.over(new byte[size]);
        page.revision(revision);
        page.data[Page.LEVEL_AT] = (byte) level;
        page.dirEnd(Page.START);
        page.maxFree(size - Page.START);
        page.totalFree(size - Page.START);
        return page;
    }

    /**
     * The root leaf of an empty table, holding only the root item.
     *
     * @param size Block size
     * @param revision Revision writing the block
     * @return Page over new bytes
     */
    static Page fakeRoot(final int size, final long revision) {
        final Page page = Page.of(Block.fakeRoot(new BlockSize(size)));
        page.revision(revision);
        return page;
    }

    /**
     * Copy of the page.
     *
     * @return Page over copied bytes
     */
    Page copy() {
        return Page.over(this.data.clone());
    }

    /**
     * Block bytes.
     *
     * @return Fresh copy of the bytes
     */
    byte[] bytes() {
        return this.data.clone();
    }

    /**
     * Writes the page as a block.
     *
     * @param store Store of the blocks
     * @param block Block number
     */
    void store(final BlockStore store, final long block) {
        store.write(new BlockNumber(block), this.data);
    }

    /**
     * Revision that wrote the block.
     *
     * @return Unsigned 32-bit revision
     */
    long revision() {
        return Integer.toUnsignedLong(this.view.getInt(0));
    }

    /**
     * Sets the revision that writes the block.
     *
     * @param revision Unsigned 32-bit revision
     */
    void revision(final long revision) {
        this.view.putInt(0, (int) revision);
    }

    /**
     * Level of the block.
     *
     * @return Level, 0 for a leaf
     */
    int level() {
        return Byte.toUnsignedInt(this.data[Page.LEVEL_AT]);
    }

    /**
     * Largest contiguous free run, between directory and items.
     *
     * @return Size in bytes
     */
    int maxFree() {
        return Short.toUnsignedInt(this.view.getShort(Page.MAX_AT));
    }

    /**
     * Total free space.
     *
     * @return Size in bytes
     */
    int totalFree() {
        return Short.toUnsignedInt(this.view.getShort(Page.TOTAL_AT));
    }

    /**
     * End of the item directory.
     *
     * @return Offset just past the last entry
     */
    int dirEnd() {
        return Short.toUnsignedInt(this.view.getShort(Page.END_AT));
    }

    /**
     * Sets the total free space.
     *
     * @param bytes Free space in bytes
     */
    void totalFree(final int bytes) {
        this.view.putShort(Page.TOTAL_AT, (short) bytes);
    }

    /**
     * Offset of the item at a directory position.
     *
     * @param pos Directory offset
     * @return Item offset
     */
    int entry(final int pos) {
        return Short.toUnsignedInt(this.view.getShort(pos));
    }

    /**
     * Sets the item offset at a directory position.
     *
     * @param pos Directory offset
     * @param offset Item offset
     */
    void entry(final int pos, final int offset) {
        this.view.putShort(pos, (short) offset);
    }

    /**
     * Size of the item at a directory position.
     *
     * @param pos Directory offset
     * @return Item bytes
     */
    int itemSize(final int pos) {
        return this.sizeAt(this.entry(pos));
    }

    /**
     * Tests whether the leaf item at a position is the last of its tag.
     *
     * @param pos Directory offset
     * @return True for the last component
     */
    boolean last(final int pos) {
        return (this.data[this.entry(pos)] & LeafItem.LAST_BIT) != 0;
    }

    /**
     * Child block of the branch item at a position.
     *
     * @param pos Directory offset
     * @return Block number
     */
    long child(final int pos) {
        return Integer.toUnsignedLong(this.view.getInt(this.entry(pos)));
    }

    /**
     * Sets the child block of the branch item at a position.
     *
     * @param pos Directory offset
     * @param block Block number
     */
    void child(final int pos, final long block) {
        this.view.putInt(this.entry(pos), (int) block);
    }

    /**
     * Key bytes of the item at a position.
     *
     * @param pos Directory offset
     * @return Key bytes
     */
    byte[] key(final int pos) {
        final int start = this.keyAt(this.entry(pos));
        return Arrays.copyOfRange(
            this.data, start + 1, start + 1 + Byte.toUnsignedInt(this.data[start])
        );
    }

    /**
     * Component number of the item at a position.
     *
     * @param pos Directory offset
     * @return Number, from 1 in leaves
     */
    int component(final int pos) {
        return this.componentAt(this.entry(pos));
    }

    /**
     * Compares a draft item with the item at a position.
     *
     * @param pos Directory offset
     * @param draft Item compared
     * @return Negative, zero or positive as the draft sorts before, at or
     *  after the item
     */
    int order(final int pos, final Draft draft) {
        final int offset = this.entry(pos);
        final int start = this.keyAt(offset) + 1;
        int diff = draft.compare(
            this.data, start, Byte.toUnsignedInt(this.data[start - 1])
        );
        if (diff == 0) {
            diff = draft.component() - this.componentAt(offset);
        }
        return diff;
    }

    /**
     * Finds the last leaf item at or before a draft.
     *
     * @param draft Item sought
     * @return Directory offset, {@code START - ENTRY} before every item
     */
    int findLeaf(final Draft draft) {
        return this.search(Page.START - Page.ENTRY, draft);
    }

    /**
     * Finds the branch item whose child may hold a draft; the first item
     * is never compared.
     *
     * @param draft Item sought
     * @return Directory offset
     */
    int findBranch(final Draft draft) {
        return this.search(Page.START, draft);
    }

    /**
     * Adds an item at a directory position, into the largest free run.
     *
     * @param item Item bytes
     * @param pos Directory offset that the item takes
     */
    void insert(final byte[] item, final int pos) {
        final int needed = item.length + Page.ENTRY;
        final int max = this.maxFree() - needed;
        final int end = this.dirEnd();
        System.arraycopy(this.data, pos, this.data, pos + Page.ENTRY, end - pos);
        this.dirEnd(end + Page.ENTRY);
        final int offset = end + Page.ENTRY + max;
        this.entry(pos, offset);
        System.arraycopy(item, 0, this.data, offset, item.length);
        this.maxFree(max);
        this.totalFree(this.totalFree() - needed);
    }

    /**
     * Overwrites the item at a position with one no larger.
     *
     * @param pos Directory offset
     * @param item Item bytes
     */
    void replace(final int pos, final byte[] item) {
        System.arraycopy(item, 0, this.data, this.entry(pos), item.length);
    }

    /**
     * Moves the item at a position into the largest free run.
     *
     * @param pos Directory offset
     * @param item Item bytes, at most {@link #maxFree()} long
     */
    void relocate(final int pos, final byte[] item) {
        final int max = this.maxFree() - item.length;
        final int offset = this.dirEnd() + max;
        System.arraycopy(item, 0, this.data, offset, item.length);
        this.entry(pos, offset);
        this.maxFree(max);
    }

    /**
     * Removes the item at a directory position.
     *
     * @param pos Directory offset
     */
    void remove(final int pos) {
        final int len = this.itemSize(pos);
        final int end = this.dirEnd() - Page.ENTRY;
        System.arraycopy(this.data, pos + Page.ENTRY, this.data, pos, end - pos);
        this.dirEnd(end);
        this.maxFree(this.maxFree() + Page.ENTRY);
        this.totalFree(this.totalFree() + len + Page.ENTRY);
    }

    /**
     * Keeps the directory entries from a position on, moved to the start.
     *
     * @param pos Directory offset of the first entry kept
     */
    void keepFrom(final int pos) {
        final int residue = this.dirEnd() - pos;
        System.arraycopy(this.data, pos, this.data, Page.START, residue);
        this.dirEnd(Page.START + residue);
    }

    /**
     * Keeps the directory entries before a position.
     *
     * @param pos Directory offset of the first entry dropped
     */
    void keepBefore(final int pos) {
        this.dirEnd(pos);
    }

    /**
     * Moves every item to the end of the block, so that the free space is
     * one run.
     *
     * @param scratch Buffer of the block size
     */
    void compact(final byte[] scratch) {
        int end = this.data.length;
        final int dir = this.dirEnd();
        for (int pos = Page.START; pos < dir; pos += Page.ENTRY) {
            final int offset = this.entry(pos);
            final int len = this.sizeAt(offset);
            end -= len;
            if (end < 0) {
                throw new DatabaseCorruptError(
                    "Item size extends outside block"
                );
            }
            System.arraycopy(this.data, offset, scratch, end, len);
            this.entry(pos, end);
        }
        System.arraycopy(scratch, end, this.data, end, this.data.length - end);
        final int free = end - dir;
        if (free < 0) {
            throw new DatabaseCorruptError(
                "Items overlap with item pointers"
            );
        }
        this.totalFree(free);
        this.maxFree(free);
    }

    /**
     * Directory position that splits the item bytes about in half.
     *
     * @return Directory offset
     */
    int midPoint() {
        final int dir = this.dirEnd();
        final int size = this.data.length - this.totalFree() - dir;
        int sum = 0;
        int mid = dir;
        for (int pos = Page.START; pos < dir; pos += Page.ENTRY) {
            final int len = this.itemSize(pos);
            sum += 2 * len;
            if (sum >= size) {
                mid = pos;
                if (len >= sum - size) {
                    mid += Page.ENTRY;
                }
                break;
            }
        }
        return mid;
    }

    /**
     * Turns the branch item at a position into one with an empty key,
     * keeping its child.
     *
     * @param pos Directory offset
     */
    void nullKey(final int pos) {
        final int offset = this.entry(pos);
        this.totalFree(
            this.totalFree()
                + Byte.toUnsignedInt(this.data[offset + BlockNumber.BYTES])
        );
        this.data[offset + BlockNumber.BYTES] = 0;
        this.view.putShort(
            offset + BlockNumber.BYTES + ItemKey.LENGTH_BYTES, (short) 0
        );
    }

    private static Page over(final byte[] data) {
        return new Page(data, ByteBuffer.wrap(data));
    }

    private int search(final int low, final Draft draft) {
        int left = low;
        int right = this.dirEnd();
        while (right - left > Page.ENTRY) {
            final int mid = left + (right - left) / (2 * Page.ENTRY) * Page.ENTRY;
            final int diff = this.order(mid, draft);
            if (diff < 0) {
                right = mid;
            } else {
                left = mid;
                if (diff == 0) {
                    break;
                }
            }
        }
        return left;
    }

    private void dirEnd(final int offset) {
        this.view.putShort(Page.END_AT, (short) offset);
    }

    private void maxFree(final int bytes) {
        this.view.putShort(Page.MAX_AT, (short) bytes);
    }

    private int sizeAt(final int offset) {
        final int size;
        if (this.leaf()) {
            size = (Short.toUnsignedInt(this.view.getShort(offset))
                & LeafItem.SIZE_MASK) + LeafItem.SIZE_BIAS;
        } else {
            size = Byte.toUnsignedInt(this.data[offset + BlockNumber.BYTES])
                + BlockNumber.BYTES + ItemKey.LENGTH_BYTES
                + TableItem.COUNT_BYTES;
        }
        return size;
    }

    private int componentAt(final int offset) {
        final int start = this.keyAt(offset);
        int number = 1;
        if (!this.leaf() || (this.data[offset] & LeafItem.FIRST_BIT) == 0) {
            number = Short.toUnsignedInt(
                this.view.getShort(
                    start + 1 + Byte.toUnsignedInt(this.data[start])
                )
            );
        }
        return number;
    }

    private int keyAt(final int offset) {
        int start = offset + BlockNumber.BYTES;
        if (this.leaf()) {
            start = offset + LeafItem.SIZE_BYTES;
        }
        return start;
    }

    private boolean leaf() {
        return this.data[Page.LEVEL_AT] == 0;
    }
}
