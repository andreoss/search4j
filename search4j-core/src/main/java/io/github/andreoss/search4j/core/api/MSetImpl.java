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
 * An immutable match set.
 *
 * @since 0.1.0
 */
final class MSetImpl implements MSet {

    /**
     * Matches in result order.
     */
    private final List<MSet.Item> found;

    /**
     * Number of matches considered.
     */
    private final TermCount size;

    /**
     * Estimated maximum number of matches.
     */
    private final TermCount maxsize;

    /**
     * Creates a match set.
     *
     * @param found Matches in result order
     * @param size Number of matches considered
     * @param maxsize Estimated maximum number of matches
     */
    MSetImpl(
        final List<MSet.Item> found, final TermCount size,
        final TermCount maxsize
    ) {
        this.found = found;
        this.size = size;
        this.maxsize = maxsize;
    }

    @Override
    public TermCount size() {
        return this.size;
    }

    @Override
    public TermCount maxSize() {
        return this.maxsize;
    }

    @Override
    public Item first() {
        if (this.found.isEmpty()) {
            throw new NoSuchElementException("Empty MSet");
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
