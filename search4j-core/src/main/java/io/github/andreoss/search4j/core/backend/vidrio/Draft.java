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

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Leaf item being built from a key and one chunk of its tag.
 *
 * @since 0.1.0
 */
final class Draft {

    /**
     * Offset of the key bytes.
     */
    private static final int KEY_AT = LeafItem.SIZE_BYTES + ItemKey.LENGTH_BYTES;

    /**
     * Item bytes, of the block size.
     */
    private final byte[] data;

    /**
     * Big-endian view of the bytes.
     */
    private final ByteBuffer view;

    /**
     * Creates an empty draft.
     *
     * @param size Block size
     */
    Draft(final int size) {
        this(new byte[size]);
    }

    /**
     * Creates a draft over its bytes.
     *
     * @param data Item bytes
     */
    private Draft(final byte[] data) {
        this(data, ByteBuffer.wrap(data));
    }

    /**
     * Creates a draft over its bytes and their view.
     *
     * @param data Item bytes
     * @param view Big-endian view of the same bytes
     */
    private Draft(final byte[] data, final ByteBuffer view) {
        this.data = data;
        this.view = view;
    }

    /**
     * Sets the key, as component 1.
     *
     * @param key Key bytes
     */
    void form(final byte[] key) {
        if (key.length > ItemKey.MAX_LENGTH) {
            throw new InvalidArgumentError(
                String.format(
                    "Key too long: length was %d bytes, maximum length of a key is %d bytes",
                    key.length, ItemKey.MAX_LENGTH
                )
            );
        }
        this.data[LeafItem.SIZE_BYTES] = (byte) key.length;
        this.data[0] = (byte) (this.data[0] | LeafItem.FIRST_BIT);
        System.arraycopy(key, 0, this.data, Draft.KEY_AT, key.length);
    }

    /**
     * Sets the tag bytes of the item and its size, clearing its flags.
     *
     * @param offset Offset of the tag bytes in the item
     * @param chunk Tag bytes
     */
    void chunk(final int offset, final ByteBuffer chunk) {
        final int len = chunk.remaining();
        chunk.get(this.data, offset, len);
        this.view.putShort(0, (short) (offset + len - LeafItem.SIZE_BIAS));
    }

    /**
     * Sets the flags and component number of the item.
     *
     * @param compressed Whether the tag is compressed
     * @param number Component number from 1
     * @param count Number of components
     */
    void flags(final boolean compressed, final int number, final int count) {
        if (compressed) {
            this.data[0] = (byte) (this.data[0] | LeafItem.COMPRESSED_BIT);
        }
        if (number == count) {
            this.data[0] = (byte) (this.data[0] | LeafItem.LAST_BIT);
        }
        if (number == 1) {
            this.data[0] = (byte) (this.data[0] | LeafItem.FIRST_BIT);
        } else {
            this.component(number);
        }
    }

    /**
     * Sets the component number, above 1.
     *
     * @param number Component number
     */
    void component(final int number) {
        this.data[0] = (byte) (this.data[0] & ~LeafItem.FIRST_BIT);
        this.view.putShort(Draft.KEY_AT + this.keyLength(), (short) number);
    }

    /**
     * Component number of the item.
     *
     * @return Number from 1
     */
    int component() {
        int number = 1;
        if ((this.data[0] & LeafItem.FIRST_BIT) == 0) {
            number = Short.toUnsignedInt(
                this.view.getShort(Draft.KEY_AT + this.keyLength())
            );
        }
        return number;
    }

    /**
     * Length of the key.
     *
     * @return Key bytes
     */
    int keyLength() {
        return Byte.toUnsignedInt(this.data[LeafItem.SIZE_BYTES]);
    }

    /**
     * Size of the item.
     *
     * @return Item bytes
     */
    int size() {
        return (Short.toUnsignedInt(this.view.getShort(0)) & LeafItem.SIZE_MASK)
            + LeafItem.SIZE_BIAS;
    }

    /**
     * Item bytes.
     *
     * @return Copy of the item
     */
    byte[] item() {
        return Arrays.copyOf(this.data, this.size());
    }

    /**
     * Compares the key with key bytes of a block.
     *
     * @param block Block bytes
     * @param start Offset of the key bytes
     * @param len Key length
     * @return Negative, zero or positive as the key sorts before, at or
     *  after the block key
     */
    int compare(final byte[] block, final int start, final int len) {
        return Arrays.compareUnsigned(
            this.data, Draft.KEY_AT, Draft.KEY_AT + this.keyLength(),
            block, start, start + len
        );
    }
}
