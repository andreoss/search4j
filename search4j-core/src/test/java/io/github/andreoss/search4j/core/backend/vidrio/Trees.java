/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.ser.Zlib;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds B-trees in memory the way the reference writer lays them out.
 *
 * @since 0.1.0
 */
final class Trees {

    /**
     * Revision of every block built.
     */
    static final RevisionNumber REVISION = new RevisionNumber(7L);

    /**
     * Utility class, no instances.
     */
    private Trees() {
    }

    /**
     * Key bytes of a text key.
     *
     * @param text Key text
     * @return ASCII bytes
     */
    static byte[] key(final String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Leaf items of entries, the root item first.
     *
     * @param keys Keys in order
     * @param tags Tag of each key
     * @param compress Whether tags are stored compressed
     * @return Leaf items in key order
     */
    static List<LeafItem> items(
        final List<String> keys, final List<byte[]> tags,
        final boolean compress
    ) {
        final List<LeafItem> items = new ArrayList<>(keys.size() + 1);
        items.add(LeafItem.root());
        for (int idx = 0; idx < keys.size(); ++idx) {
            byte[] tag = tags.get(idx);
            if (compress) {
                tag = Zlib.compress(tag);
            }
            items.addAll(
                Trees.split(
                    ItemKey.of(Trees.key(keys.get(idx))), tag, compress
                )
            );
        }
        return items;
    }

    /**
     * Components of one tag.
     *
     * @param key Key of the tag
     * @param tag Stored tag bytes
     * @param compress Whether the bytes are compressed
     * @return Items of the tag in component order
     */
    static List<LeafItem> split(
        final ItemKey key, final byte[] tag, final boolean compress
    ) {
        final int chunk = 7;
        final int parts = Math.max(1, (tag.length + chunk - 1) / chunk);
        final List<LeafItem> items = new ArrayList<>(parts);
        for (int part = 1; part <= parts; ++part) {
            items.add(
                LeafItem.of(
                    key, new TagPart(part, compress, part == parts),
                    Arrays.copyOfRange(
                        tag, (part - 1) * chunk,
                        Math.min(tag.length, part * chunk)
                    )
                )
            );
        }
        return items;
    }

    /**
     * Lays out items as a tree with at most so many items per block.
     *
     * @param items Leaf items in order
     * @param fanout Most items per block
     * @param size Block size
     * @return Blocks, the root last
     */
    static MemoryBlocks tree(
        final List<LeafItem> items, final int fanout, final BlockSize size
    ) {
        final Map<Long, byte[]> data = new HashMap<>();
        List<? extends TableItem> layer = items;
        int level = 0;
        while (true) {
            final List<List<TableItem>> groups =
                Trees.groups(layer, fanout, size);
            final List<BranchItem> parents = new ArrayList<>(groups.size());
            for (final List<TableItem> group : groups) {
                final long num = data.size();
                data.put(num, Trees.block(level, group, size));
                parents.add(
                    new BranchItem(
                        group.get(0).key(), group.get(0).component(),
                        new BlockNumber(num)
                    )
                );
            }
            if (groups.size() == 1) {
                break;
            }
            layer = parents;
            ++level;
        }
        return new MemoryBlocks(data, size);
    }

    /**
     * Root of a tree built by {@link #tree}.
     *
     * @param blocks Blocks of the tree
     * @return Root, the last block
     */
    static TableRoot root(final MemoryBlocks blocks) {
        final long num = blocks.count() - 1L;
        return new TableRoot(
            new BlockNumber(num),
            Byte.toUnsignedInt(blocks.bytes(num)[4]),
            blocks.size(), Trees.REVISION, false
        );
    }

    /**
     * Encodes one block.
     *
     * @param level Level of the block
     * @param items Items in order
     * @param size Block size
     * @return Block bytes
     */
    static byte[] block(
        final int level, final List<? extends TableItem> items,
        final BlockSize size
    ) {
        final ByteBuffer buf = ByteBuffer.allocate(size.bytes());
        final int end = BlockHeader.BYTES
            + BlockHeader.ENTRY_BYTES * items.size();
        int offset = size.bytes();
        for (int idx = 0; idx < items.size(); ++idx) {
            final byte[] item = items.get(idx).bytes();
            offset -= item.length;
            buf.put(offset, item);
            buf.putShort(
                BlockHeader.BYTES + BlockHeader.ENTRY_BYTES * idx,
                (short) offset
            );
        }
        buf.put(
            0,
            new BlockHeader(
                Trees.REVISION, level, offset - end, offset - end, end
            ).bytes()
        );
        return buf.array();
    }

    private static List<List<TableItem>> groups(
        final List<? extends TableItem> layer, final int fanout,
        final BlockSize size
    ) {
        final List<List<TableItem>> groups = new ArrayList<>(1);
        List<TableItem> group = new ArrayList<>(fanout);
        int used = BlockHeader.BYTES;
        for (final TableItem item : layer) {
            final int need = item.size() + BlockHeader.ENTRY_BYTES;
            if (group.size() == fanout || used + need > size.bytes()) {
                groups.add(group);
                group = new ArrayList<>(fanout);
                used = BlockHeader.BYTES;
            }
            group.add(item);
            used += need;
        }
        groups.add(group);
        return groups;
    }
}
