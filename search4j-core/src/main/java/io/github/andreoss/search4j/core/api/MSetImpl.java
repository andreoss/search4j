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
     * Bounds and estimate of the number of matches, after and before
     * collapsing.
     */
    private final TermCount[] bounds;

    /**
     * Creates a match set.
     *
     * @param found Matches in result order
     * @param bounds Lower bound, estimate and upper bound of the number of
     *  matches, then the same before collapsing
     */
    MSetImpl(final List<MSet.Item> found, final TermCount... bounds) {
        this.found = found;
        this.bounds = bounds.clone();
    }

    @Override
    public TermCount size() {
        return new TermCount(this.found.size());
    }

    @Override
    public TermCount maxSize() {
        return this.size();
    }

    @Override
    public TermCount matchesLowerBound() {
        return this.bounds[0];
    }

    @Override
    public TermCount matchesEstimated() {
        return new RoundedEstimate(this.bounds[0], this.bounds[1], this.bounds[2])
            .value();
    }

    @Override
    public TermCount matchesUpperBound() {
        return this.bounds[2];
    }

    @Override
    public TermCount uncollapsedMatchesLowerBound() {
        return this.bounds[3];
    }

    @Override
    public TermCount uncollapsedMatchesEstimated() {
        return new RoundedEstimate(this.bounds[3], this.bounds[4], this.bounds[5])
            .value();
    }

    @Override
    public TermCount uncollapsedMatchesUpperBound() {
        return this.bounds[5];
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
