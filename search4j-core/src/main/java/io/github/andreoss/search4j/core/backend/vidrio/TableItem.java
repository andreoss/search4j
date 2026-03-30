/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * An item of a B-tree block: a key, its component number and a payload.
 *
 * <p>Multi-byte fields are big-endian.</p>
 *
 * @since 0.1.0
 */
public interface TableItem {

    /**
     * Width of the component number that ends a stored key.
     */
    int COUNT_BYTES = 2;

    /**
     * Key of the item.
     *
     * @return Key
     */
    ItemKey key();

    /**
     * Component number that orders items sharing a key.
     *
     * @return Component number
     */
    int component();

    /**
     * Encoded size in bytes.
     *
     * @return Size of {@link #bytes()}
     */
    int size();

    /**
     * Encodes the item.
     *
     * @return Item bytes
     */
    byte[] bytes();

    /**
     * Compares by key, then by component number.
     *
     * @param other Item to compare with
     * @return Negative, zero or positive
     */
    default int order(final TableItem other) {
        int diff = this.key().compareTo(other.key());
        if (diff == 0) {
            diff = Integer.compare(this.component(), other.component());
        }
        return diff;
    }
}
