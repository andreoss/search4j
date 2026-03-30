/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.ser.ZlibChunks;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A B-tree block read from a table, searched in place.
 *
 * <p>Items are addressed by directory index; reading them allocates nothing
 * except the keys handed out.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class Block {

    /**
     * Number of the block in its file.
     */
    private final BlockNumber num;

    /**
     * Block bytes, never exposed.
     */
    private final byte[] data;

    /**
     * Big-endian view of the bytes.
     */
    private final ByteBuffer view;

    /**
     * Creates a block over bytes already checked.
     *
     * @param num Number of the block
     * @param data Block bytes
     */
    private Block(final BlockNumber num, final byte[] data) {
        this(num, data, ByteBuffer.wrap(data));
    }

    /**
     * Creates a block over its parts.
     *
     * @param num Number of the block
     * @param data Block bytes
     * @param view View of the same bytes
     */
    private Block(
        final BlockNumber num, final byte[] data, final ByteBuffer view
    ) {
        this.num = num;
        this.data = data;
        this.view = view;
    }

    /**
     * Wraps block bytes, checking the directory end of a tree block.
     *
     * @param num Number of the block
     * @param data Block bytes of a valid block size, not copied
     * @return Block
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static Block parse(final BlockNumber num, final byte[] data) {
        final Block block = new Block(num, data);
        final int end = block.dirEnd();
        if (block.level() != BlockHeader.LEVEL_FREELIST
            && (end < BlockHeader.BYTES || end > data.length)) {
            throw new DatabaseCorruptError(
                String.format("Bad directory end in block %s", num)
            );
        }
        return block;
    }

    /**
     * The root of an empty table: a leaf holding only the root item.
     *
     * @param size Block size
     * @return Block 0 at revision 0
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static Block fakeRoot(final BlockSize size) {
        final byte[] item = LeafItem.root().bytes();
        final int offset = size.bytes() - item.length;
        final int end = BlockHeader.BYTES + BlockHeader.ENTRY_BYTES;
        final int free = offset - end;
        final ByteBuffer buf = ByteBuffer.allocate(size.bytes());
        buf.put(
            new BlockHeader(new RevisionNumber(0L), 0, free, free, end).bytes()
        );
        buf.putShort((short) offset);
        buf.put(offset, item);
        return new Block(new BlockNumber(0L), buf.array());
    }

    /**
     * Number of the block in its file.
     *
     * @return Block number
     */
    public BlockNumber number() {
        return this.num;
    }

    /**
     * Revision that last wrote the block.
     *
     * @return Revision
     */
    public RevisionNumber revision() {
        return RevisionNumber.fromBits(this.view.getInt(0));
    }

    /**
     * Height of the block above the leaves.
     *
     * @return Level, 0 for a leaf
     */
    public int level() {
        return Byte.toUnsignedInt(this.data[4]);
    }

    /**
     * Header of the block.
     *
     * @return Decoded header
     */
    public BlockHeader header() {
        return BlockHeader.parse(this.data);
    }

    /**
     * Number of items in the block.
     *
     * @return Directory entries
     */
    public int entries() {
        return (this.dirEnd() - BlockHeader.BYTES) / BlockHeader.ENTRY_BYTES;
    }

    /**
     * Finds the last leaf item at or before a key's first component.
     *
     * @param key Key bytes
     * @param len Number of key bytes used
     * @return Directory index, -1 when every item is after the key
     */
    public int searchLeaf(final byte[] key, final int len) {
        int low = -1;
        int high = this.entries();
        while (high - low > 1) {
            final int mid = low + (high - low) / 2;
            final int diff = this.compareLeaf(mid, key, len);
            if (diff < 0) {
                high = mid;
            } else {
                low = mid;
                if (diff == 0) {
                    break;
                }
            }
        }
        return low;
    }

    /**
     * Finds the branch item whose child may hold a key's first component.
     *
     * <p>The first item is never compared: its key is the lower bound of
     * the block.</p>
     *
     * @param key Key bytes
     * @param len Number of key bytes used
     * @return Directory index from 0
     */
    public int searchBranch(final byte[] key, final int len) {
        int low = 0;
        int high = this.entries();
        while (high - low > 1) {
            final int mid = low + (high - low) / 2;
            final int diff = this.compareBranch(mid, key, len);
            if (diff < 0) {
                high = mid;
            } else {
                low = mid;
                if (diff == 0) {
                    break;
                }
            }
        }
        return low;
    }

    /**
     * Compares a key's first component with a leaf item.
     *
     * @param index Directory index
     * @param key Key bytes
     * @param len Number of key bytes used
     * @return Negative, zero or positive as the key sorts before, at or after
     */
    public int compareLeaf(final int index, final byte[] key, final int len) {
        final int start = this.leafKeyAt(index) + ItemKey.LENGTH_BYTES;
        int diff = Arrays.compareUnsigned(
            key, 0, len, this.data, start, start + this.keyLength(start - 1)
        );
        if (diff == 0) {
            diff = 1 - this.component(index);
        }
        return diff;
    }

    /**
     * Key of a leaf item.
     *
     * @param index Directory index
     * @return Key
     */
    public ItemKey leafKey(final int index) {
        return ItemKey.parse(this.data, this.leafKeyAt(index));
    }

    /**
     * Key of a branch item.
     *
     * @param index Directory index
     * @return Key
     */
    public ItemKey branchKey(final int index) {
        return ItemKey.parse(this.data, this.branchKeyAt(index));
    }

    /**
     * Child block of a branch item.
     *
     * @param index Directory index
     * @return Block number of the child
     */
    public BlockNumber child(final int index) {
        return BlockNumber.fromBits(this.view.getInt(this.branchItem(index)));
    }

    /**
     * Tests whether a leaf item is the first component of its tag.
     *
     * @param index Directory index
     * @return True for component 1
     */
    public boolean first(final int index) {
        return this.flag(index, LeafItem.FIRST_BIT);
    }

    /**
     * Tests whether a leaf item is the last component of its tag.
     *
     * @param index Directory index
     * @return True for the last component
     */
    public boolean last(final int index) {
        return this.flag(index, LeafItem.LAST_BIT);
    }

    /**
     * Tests whether a leaf item's tag bytes are compressed.
     *
     * @param index Directory index
     * @return True when compressed
     */
    public boolean compressed(final int index) {
        return this.flag(index, LeafItem.COMPRESSED_BIT);
    }

    /**
     * Component number of a leaf item.
     *
     * @param index Directory index
     * @return Number from 1
     */
    public int component(final int index) {
        int number = 1;
        if (!this.first(index)) {
            number = Short.toUnsignedInt(
                this.view.getShort(this.countOffset(index))
            );
        }
        return number;
    }

    /**
     * Appends the tag bytes of a leaf item.
     *
     * @param index Directory index
     * @param out Sink of the bytes
     */
    public void appendChunk(final int index, final ByteArrayOutputStream out) {
        final int start = this.chunkStart(index);
        out.write(this.data, start, this.chunkEnd(index) - start);
    }

    /**
     * Inflates the compressed tag bytes of a leaf item.
     *
     * @param index Directory index
     * @param inflater Decompressor of the whole tag
     * @param out Sink of the inflated bytes
     * @return True once the compressed stream ends
     * @throws IOException If the bytes are not valid compressed data
     */
    public boolean inflateChunk(
        final int index, final ZlibChunks inflater,
        final ByteArrayOutputStream out
    ) throws IOException {
        final int start = this.chunkStart(index);
        return inflater.feed(
            ByteBuffer.wrap(this.data, start, this.chunkEnd(index) - start),
            out
        );
    }

    @Override
    public String toString() {
        return String.format(
            "Block[number=%s, level=%d, entries=%d]",
            this.num, this.level(), this.entries()
        );
    }

    private int compareBranch(
        final int index, final byte[] key, final int len
    ) {
        final int start = this.branchKeyAt(index) + ItemKey.LENGTH_BYTES;
        final int klen = this.keyLength(start - 1);
        int diff = Arrays.compareUnsigned(
            key, 0, len, this.data, start, start + klen
        );
        if (diff == 0) {
            diff = 1 - Short.toUnsignedInt(
                this.view.getShort(
                    this.within(start + klen, TableItem.COUNT_BYTES)
                )
            );
        }
        return diff;
    }

    private boolean flag(final int index, final int bit) {
        return (this.data[this.leafItem(index)] & bit) != 0;
    }

    private int chunkStart(final int index) {
        int start = this.countOffset(index);
        if (!this.first(index)) {
            start += TableItem.COUNT_BYTES;
        }
        final int end = this.chunkEnd(index);
        if (start > end) {
            throw new DatabaseCorruptError(
                String.format(
                    "Leaf item key overruns item %d in block %s",
                    index, this.num
                )
            );
        }
        return start;
    }

    private int chunkEnd(final int index) {
        final int offset = this.leafItem(index);
        final int size = (Short.toUnsignedInt(this.view.getShort(offset))
            & LeafItem.SIZE_MASK) + LeafItem.SIZE_BIAS;
        return this.within(offset, size) + size;
    }

    private int countOffset(final int index) {
        final int key = this.leafKeyAt(index);
        final int end = key + ItemKey.LENGTH_BYTES + this.keyLength(key);
        int offset = end;
        if (!this.first(index)) {
            offset = this.within(end, TableItem.COUNT_BYTES);
        }
        return offset;
    }

    private int leafKeyAt(final int index) {
        return this.leafItem(index) + LeafItem.SIZE_BYTES;
    }

    private int branchKeyAt(final int index) {
        return this.branchItem(index) + BlockNumber.BYTES;
    }

    private int leafItem(final int index) {
        return this.within(this.item(index), LeafItem.SIZE_BIAS);
    }

    private int branchItem(final int index) {
        return this.within(
            this.item(index),
            BlockNumber.BYTES + ItemKey.LENGTH_BYTES + TableItem.COUNT_BYTES
        );
    }

    private int keyLength(final int offset) {
        final int len = Byte.toUnsignedInt(this.data[offset]);
        this.within(offset + ItemKey.LENGTH_BYTES, len);
        return len;
    }

    private int item(final int index) {
        if (index < 0 || index >= this.entries()) {
            throw new DatabaseCorruptError(
                String.format(
                    "Bad directory index %d in block %s", index, this.num
                )
            );
        }
        return Short.toUnsignedInt(
            this.view.getShort(
                BlockHeader.BYTES + BlockHeader.ENTRY_BYTES * index
            )
        );
    }

    private int within(final int offset, final int len) {
        if (offset + len > this.data.length) {
            throw new DatabaseCorruptError(
                String.format(
                    "Item overruns block %s at offset %d", this.num, offset
                )
            );
        }
        return offset;
    }

    private int dirEnd() {
        return Short.toUnsignedInt(this.view.getShort(9));
    }
}
