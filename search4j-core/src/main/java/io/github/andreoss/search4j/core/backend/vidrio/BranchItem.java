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
 * Item of a branch block: a key and the child block it leads to.
 *
 * <p>Layout: the four-byte child block number, the length-prefixed key and
 * the two-byte component number.</p>
 *
 * @param key Smallest key reachable through the child
 * @param component Component number from 0 to 65535
 * @param child Block number of the child
 * @since 0.1.0
 */
public record BranchItem(ItemKey key, int component, BlockNumber child)
    implements TableItem {

    /**
     * Item bytes besides the key bytes.
     */
    private static final int OVERHEAD =
        BlockNumber.BYTES + ItemKey.LENGTH_BYTES + TableItem.COUNT_BYTES;

    /**
     * Creates an item.
     *
     * @param key Smallest key reachable through the child
     * @param component Component number from 0 to 65535
     * @param child Block number of the child
     */
    public BranchItem {
        if (component < 0 || component > 0xFFFF) {
            throw new IllegalArgumentException(
                String.format("Bad component number: %d", component)
            );
        }
    }

    /**
     * The item with an empty key leading to a block.
     *
     * @param child Block number of the child
     * @return Item with empty key and component 0
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static BranchItem nullKey(final BlockNumber child) {
        return new BranchItem(ItemKey.EMPTY, 0, child);
    }

    /**
     * Decodes the item at an offset of a block.
     *
     * @param block Block bytes
     * @param offset Offset of the item
     * @return Item
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static BranchItem parse(final byte[] block, final int offset) {
        if (offset < 0 || offset + BranchItem.OVERHEAD > block.length) {
            throw new DatabaseCorruptError(
                String.format("Branch item offset outside block: %d", offset)
            );
        }
        final ByteBuffer buf = ByteBuffer.wrap(block);
        final ItemKey key = ItemKey.parse(
            block, offset + BlockNumber.BYTES
        );
        final int count = offset + BranchItem.OVERHEAD - TableItem.COUNT_BYTES
            + key.length();
        if (count + TableItem.COUNT_BYTES > block.length) {
            throw new DatabaseCorruptError(
                String.format("Branch item overruns block at %d", offset)
            );
        }
        return new BranchItem(
            key, Short.toUnsignedInt(buf.getShort(count)),
            BlockNumber.fromBits(buf.getInt(offset))
        );
    }

    @Override
    public int size() {
        return BranchItem.OVERHEAD + this.key.length();
    }

    @Override
    public byte[] bytes() {
        return ByteBuffer.allocate(this.size())
            .putInt(this.child.bits())
            .put(this.key.bytes())
            .putShort((short) this.component)
            .array();
    }
}
