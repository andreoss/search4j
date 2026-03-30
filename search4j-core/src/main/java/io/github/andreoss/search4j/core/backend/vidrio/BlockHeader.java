/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import java.nio.ByteBuffer;

/**
 * Header at the start of every B-tree block.
 *
 * <p>Layout: revision (4 bytes), level (1), largest free run (2), total free
 * bytes (2) and directory end offset (2), all big-endian. A directory of
 * two-byte item offsets follows the header.</p>
 *
 * @param revision Revision that last wrote the block
 * @param level Height above the leaves, 0 for a leaf
 * @param maxFree Largest contiguous free space in bytes
 * @param totalFree Total free space in bytes
 * @param dirEnd Offset just past the last directory entry
 * @since 0.1.0
 */
public record BlockHeader(
    RevisionNumber revision, int level, int maxFree, int totalFree, int dirEnd
) {

    /**
     * Encoded size in bytes, the offset of the first directory entry.
     */
    public static final int BYTES = 11;

    /**
     * Width of a directory entry, the offset of an item in its block.
     */
    public static final int ENTRY_BYTES = 2;

    /**
     * Level of a block that belongs to the free list.
     */
    public static final int LEVEL_FREELIST = 254;

    /**
     * Creates a header.
     *
     * @param revision Revision that last wrote the block
     * @param level Level from 0 to 255
     * @param maxFree Largest free run from 0 to 65535
     * @param totalFree Total free bytes from 0 to 65535
     * @param dirEnd Directory end, 11 plus a multiple of two, at most 65535
     */
    public BlockHeader {
        if (level < 0 || level > 0xFF) {
            throw new IllegalArgumentException(
                String.format("Bad block level: %d", level)
            );
        }
        BlockHeader.requirePair(maxFree);
        BlockHeader.requirePair(totalFree);
        BlockHeader.requirePair(dirEnd);
        if (!BlockHeader.directory(dirEnd)) {
            throw new IllegalArgumentException(
                String.format("Bad directory end: %d", dirEnd)
            );
        }
    }

    /**
     * Decodes the header at the start of a block.
     *
     * @param block Block bytes, at least {@link #BYTES} long
     * @return Header
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static BlockHeader parse(final byte[] block) {
        if (block.length < BlockHeader.BYTES) {
            throw new DatabaseCorruptError(
                String.format("Block too short for header: %d", block.length)
            );
        }
        final ByteBuffer buf = ByteBuffer.wrap(block);
        final int end = Short.toUnsignedInt(buf.getShort(9));
        if (!BlockHeader.directory(end)) {
            throw new DatabaseCorruptError(
                String.format("Bad directory end in block: %d", end)
            );
        }
        return new BlockHeader(
            RevisionNumber.fromBits(buf.getInt(0)),
            Byte.toUnsignedInt(buf.get(4)),
            Short.toUnsignedInt(buf.getShort(5)),
            Short.toUnsignedInt(buf.getShort(7)),
            end
        );
    }

    /**
     * Encodes the header.
     *
     * @return Header of {@link #BYTES} bytes
     */
    public byte[] bytes() {
        return ByteBuffer.allocate(BlockHeader.BYTES)
            .putInt(this.revision.bits())
            .put((byte) this.level)
            .putShort((short) this.maxFree)
            .putShort((short) this.totalFree)
            .putShort((short) this.dirEnd)
            .array();
    }

    /**
     * Tests whether the block is a leaf.
     *
     * @return True at level 0
     */
    public boolean leaf() {
        return this.level == 0;
    }

    /**
     * Tests whether the block belongs to the free list.
     *
     * @return True at the free-list level
     */
    public boolean freelist() {
        return this.level == BlockHeader.LEVEL_FREELIST;
    }

    /**
     * Number of directory entries.
     *
     * @return Items in the block
     */
    public int entries() {
        return (this.dirEnd - BlockHeader.BYTES) / BlockHeader.ENTRY_BYTES;
    }

    /**
     * Offset of a directory entry in the block.
     *
     * @param index Entry index, below {@link #entries()}
     * @return Offset of the entry's two bytes
     */
    public int entryOffset(final int index) {
        if (index < 0 || index >= this.entries()) {
            throw new IllegalArgumentException(
                String.format("Bad directory index: %d", index)
            );
        }
        return BlockHeader.BYTES + BlockHeader.ENTRY_BYTES * index;
    }

    private static void requirePair(final int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException(
                String.format("Field out of range: %d", value)
            );
        }
    }

    private static boolean directory(final int end) {
        return end >= BlockHeader.BYTES
            && (end - BlockHeader.BYTES) % BlockHeader.ENTRY_BYTES == 0;
    }
}
