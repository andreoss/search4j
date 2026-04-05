/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Growable vector of integers with a small initial footprint.
 *
 * <p>Capacity doubles when it is exhausted; the vector never hands out its
 * backing array.</p>
 *
 * @since 0.1.0
 */
public final class SmallVector {

    /**
     * Backing store, longer than the size.
     */
    private int[] items;

    /**
     * Number of items held.
     */
    private int size;

    /**
     * Creates an empty vector.
     */
    public SmallVector() {
        this.items = new int[4];
        this.size = 0;
    }

    /**
     * Number of items held.
     *
     * @return Item count
     */
    public int size() {
        return this.size;
    }

    /**
     * Item at an index.
     *
     * @param index Zero-based index
     * @return Item at the index
     */
    public int get(final int index) {
        SmallVector.check(index, this.size);
        return this.items[index];
    }

    /**
     * Replaces the item at an index.
     *
     * @param index Zero-based index
     * @param value Replacement item
     */
    public void set(final int index, final int value) {
        SmallVector.check(index, this.size);
        this.items[index] = value;
    }

    /**
     * Appends an item.
     *
     * @param value Item to append
     */
    public void add(final int value) {
        if (this.size == this.items.length) {
            final int[] grown = new int[this.items.length * 2];
            System.arraycopy(this.items, 0, grown, 0, this.size);
            this.items = grown;
        }
        this.items[this.size] = value;
        this.size = this.size + 1;
    }

    /**
     * Removes every item.
     */
    public void clear() {
        this.size = 0;
    }

    /**
     * Copies the items into an array.
     *
     * @return Items in order
     */
    public int[] toArray() {
        final int[] copy = new int[this.size];
        System.arraycopy(this.items, 0, copy, 0, this.size);
        return copy;
    }

    private static void check(final int index, final int size) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                String.format("Index %d, size %d", index, size)
            );
        }
    }
}
