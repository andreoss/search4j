/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An immutable expansion set.
 *
 * @since 0.1.0
 */
final class ESetImpl implements ESet {

    /**
     * Terms in rank order.
     */
    private final List<ESet.Item> found;

    /**
     * Creates an expansion set.
     *
     * @param found Terms in rank order
     */
    ESetImpl(final List<ESet.Item> found) {
        this.found = found;
    }

    @Override
    public TermCount size() {
        return new TermCount(this.found.size());
    }

    @Override
    public Item first() {
        if (this.found.isEmpty()) {
            throw new NoSuchElementException("Empty ESet");
        }
        return this.found.get(0);
    }

    @Override
    public Item get(final int index) {
        if (index < 0 || index >= this.found.size()) {
            throw new IndexOutOfBoundsException(
                String.format(
                    "Index %d, size %d", index, this.found.size()
                )
            );
        }
        return this.found.get(index);
    }

    @Override
    public List<Item> toList() {
        return List.copyOf(this.found);
    }

    @Override
    public Iterator<Item> iterator() {
        return this.found.iterator();
    }
}
