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
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Key of a B-tree item: up to 255 bytes, stored after a one-byte length.
 *
 * <p>Keys order by unsigned bytes, a prefix before its extensions.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class ItemKey implements Comparable<ItemKey> {

    /**
     * Width of the key length.
     */
    public static final int LENGTH_BYTES = 1;

    /**
     * Longest key in bytes.
     */
    public static final int MAX_LENGTH = 255;

    /**
     * The empty key, reserved for the root item.
     */
    public static final ItemKey EMPTY = new ItemKey(new byte[0]);

    /**
     * Key bytes, never exposed.
     */
    private final byte[] data;

    /**
     * Creates a key over bytes already copied.
     *
     * @param data Key bytes, at most 255
     */
    private ItemKey(final byte[] data) {
        this.data = data;
    }

    /**
     * Creates a key.
     *
     * @param data Key bytes, at most 255, copied
     * @return Key over copied bytes
     */
    public static ItemKey of(final byte[] data) {
        if (data.length > ItemKey.MAX_LENGTH) {
            throw new InvalidArgumentError(
                String.format(
                    "Key too long: length was %d bytes, maximum is %d",
                    data.length, ItemKey.MAX_LENGTH
                )
            );
        }
        return new ItemKey(data.clone());
    }

    /**
     * Decodes a length-prefixed key.
     *
     * @param block Bytes holding the key
     * @param offset Offset of the length byte
     * @return Key over copied bytes
     */
    public static ItemKey parse(final byte[] block, final int offset) {
        if (offset < 0 || offset >= block.length) {
            throw new DatabaseCorruptError(
                String.format("Key offset outside block: %d", offset)
            );
        }
        final int len = Byte.toUnsignedInt(block[offset]);
        final int start = offset + ItemKey.LENGTH_BYTES;
        if (start + len > block.length) {
            throw new DatabaseCorruptError(
                String.format("Key overruns block: %d bytes", len)
            );
        }
        return new ItemKey(Arrays.copyOfRange(block, start, start + len));
    }

    /**
     * Key bytes.
     *
     * @return Fresh copy
     */
    public byte[] data() {
        return this.data.clone();
    }

    /**
     * Number of key bytes.
     *
     * @return Length from 0 to 255
     */
    public int length() {
        return this.data.length;
    }

    /**
     * Encodes the key with its length byte.
     *
     * @return Length byte followed by the key bytes
     */
    public byte[] bytes() {
        final byte[] out = new byte[ItemKey.LENGTH_BYTES + this.data.length];
        out[0] = (byte) this.data.length;
        System.arraycopy(
            this.data, 0, out, ItemKey.LENGTH_BYTES, this.data.length
        );
        return out;
    }

    @Override
    public int compareTo(final ItemKey other) {
        return Arrays.compareUnsigned(this.data, other.data);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || other instanceof ItemKey key
            && Arrays.equals(this.data, key.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public String toString() {
        return HexFormat.of().formatHex(this.data);
    }
}
