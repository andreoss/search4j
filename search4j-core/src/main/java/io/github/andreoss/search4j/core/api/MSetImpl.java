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
     * Bounds and estimate of the number of matches.
     */
    private final TermCount[] bounds;

    /**
     * Creates a match set.
     *
     * @param found Matches in result order
     * @param bounds Lower bound, estimate and upper bound of the number of
     *  matches
     */
    MSetImpl(final List<MSet.Item> found, final TermCount... bounds) {
        this.found = found;
        this.bounds = bounds.clone();
    }

    @Override
    public TermCount size() {
        return this.matchesEstimated();
    }

    @Override
    public TermCount maxSize() {
        return this.matchesUpperBound();
    }

    @Override
    public TermCount matchesLowerBound() {
        return this.bounds[0];
    }

    @Override
    public TermCount matchesEstimated() {
        return this.bounds[1];
    }

    @Override
    public TermCount matchesUpperBound() {
        return this.bounds[2];
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
