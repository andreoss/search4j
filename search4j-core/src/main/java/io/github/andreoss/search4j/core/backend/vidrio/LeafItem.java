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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Item of a leaf block: one component of a key's tag.
 *
 * <p>Layout: a two-byte size field whose top three bits are the compressed,
 * last and first flags and whose low thirteen bits are the item size less
 * three; the length-prefixed key; a two-byte component number, omitted for
 * the first component; and the tag bytes.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class LeafItem implements TableItem {

    /**
     * Width of the size field.
     */
    public static final int SIZE_BYTES = 2;

    /**
     * Size flag of an item whose tag bytes are compressed.
     */
    public static final int COMPRESSED_BIT = 0x80;

    /**
     * Size flag of the last component of a tag.
     */
    public static final int LAST_BIT = 0x40;

    /**
     * Size flag of the first component of a tag.
     */
    public static final int FIRST_BIT = 0x20;

    /**
     * All flags of the high size byte.
     */
    public static final int FLAG_MASK = 0xE0;

    /**
     * Bits of the size field that hold the size.
     */
    public static final int SIZE_MASK = 0x1FFF;

    /**
     * Item bytes not counted by the size field.
     */
    public static final int SIZE_BIAS =
        LeafItem.SIZE_BYTES + ItemKey.LENGTH_BYTES;

    /**
     * Largest item in bytes.
     */
    public static final int MAX_SIZE = LeafItem.SIZE_MASK + LeafItem.SIZE_BIAS;

    /**
     * Items of the largest size that must fit in one block.
     */
    public static final int BLOCK_CAPACITY = 4;

    /**
     * Key of the item.
     */
    private final ItemKey name;

    /**
     * Position of the item within its tag.
     */
    private final TagPart place;

    /**
     * Tag bytes of this component, never exposed.
     */
    private final byte[] tag;

    /**
     * Creates an item from checked parts.
     *
     * @param key Key of the item
     * @param part Position within the tag
     * @param chunk Tag bytes, already copied
     */
    private LeafItem(final ItemKey key, final TagPart part, final byte[] chunk) {
        this.name = key;
        this.place = part;
        this.tag = chunk;
    }

    /**
     * Creates an item.
     *
     * @param key Key of the item
     * @param part Position within the tag
     * @param chunk Tag bytes of this component, copied
     * @return New item
     */
    public static LeafItem of(
        final ItemKey key, final TagPart part, final byte[] chunk
    ) {
        final int size = LeafItem.tagOffset(
            Objects.requireNonNull(key, "key"),
            Objects.requireNonNull(part, "part")
        ) + chunk.length;
        if (size > LeafItem.MAX_SIZE) {
            throw new InvalidArgumentError(
                String.format("Item too large: %d bytes", size)
            );
        }
        return new LeafItem(key, part, chunk.clone());
    }

    /**
     * The item of an empty table's root: empty key, sole component, no tag.
     *
     * @return Root item of {@link LeafItem#SIZE_BIAS} bytes
     */
    public static LeafItem root() {
        return LeafItem.of(ItemKey.EMPTY, TagPart.WHOLE, new byte[0]);
    }

    /**
     * Decodes the item at an offset of a block.
     *
     * @param block Block bytes
     * @param offset Offset of the item
     * @return Decoded item
     */
    public static LeafItem parse(final byte[] block, final int offset) {
        if (offset < 0 || offset + LeafItem.SIZE_BIAS > block.length) {
            throw new DatabaseCorruptError(
                String.format("Leaf item offset outside block: %d", offset)
            );
        }
        final ByteBuffer buf = ByteBuffer.wrap(block);
        final int field = Short.toUnsignedInt(buf.getShort(offset));
        final int end = offset + (field & LeafItem.SIZE_MASK)
            + LeafItem.SIZE_BIAS;
        if (end > block.length) {
            throw new DatabaseCorruptError(
                String.format("Leaf item overruns block at %d", offset)
            );
        }
        final int high = field >>> 8;
        final ItemKey key = ItemKey.parse(block, offset + LeafItem.SIZE_BYTES);
        int start = offset + LeafItem.SIZE_BIAS + key.length();
        int component = 1;
        if ((high & LeafItem.FIRST_BIT) == 0) {
            if (start + TableItem.COUNT_BYTES > end) {
                throw new DatabaseCorruptError(
                    String.format("Leaf item without count at %d", offset)
                );
            }
            component = Short.toUnsignedInt(buf.getShort(start));
            start += TableItem.COUNT_BYTES;
            if (component < 2) {
                throw new DatabaseCorruptError(
                    String.format("Bad component number at %d", offset)
                );
            }
        }
        if (start > end) {
            throw new DatabaseCorruptError(
                String.format("Leaf item key overruns item at %d", offset)
            );
        }
        return new LeafItem(
            key,
            new TagPart(
                component, (high & LeafItem.COMPRESSED_BIT) != 0,
                (high & LeafItem.LAST_BIT) != 0
            ),
            Arrays.copyOfRange(block, start, end)
        );
    }

    @Override
    public ItemKey key() {
        return this.name;
    }

    @Override
    public int component() {
        return this.place.number();
    }

    /**
     * Position of the item within its tag.
     *
     * @return Component number and flags
     */
    public TagPart part() {
        return this.place;
    }

    /**
     * Tag bytes of this component.
     *
     * @return Fresh copy
     */
    public byte[] chunk() {
        return this.tag.clone();
    }

    @Override
    public int size() {
        return LeafItem.tagOffset(this.name, this.place) + this.tag.length;
    }

    @Override
    public byte[] bytes() {
        final ByteBuffer buf = ByteBuffer.allocate(this.size());
        buf.putShort(
            (short) (this.place.flags() << 8 | this.size() - LeafItem.SIZE_BIAS)
        );
        buf.put(this.name.bytes());
        if (!this.place.first()) {
            buf.putShort((short) this.place.number());
        }
        return buf.put(this.tag).array();
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof LeafItem item
            && this.name.equals(item.name) && this.place.equals(item.place)
            && Arrays.equals(this.tag, item.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.place, Arrays.hashCode(this.tag));
    }

    @Override
    public String toString() {
        return String.format(
            "LeafItem[key=%s, part=%s, chunk=%d bytes]",
            this.name, this.place, this.tag.length
        );
    }

    private static int tagOffset(final ItemKey key, final TagPart part) {
        int offset = LeafItem.SIZE_BIAS + key.length();
        if (!part.first()) {
            offset += TableItem.COUNT_BYTES;
        }
        return offset;
    }
}
