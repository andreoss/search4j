/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator over an ascending list of positions.
 *
 * @since 0.1.0
 */
final class PositionCursor implements PositionIterator {

    /**
     * The positions in ascending order.
     */
    private final List<TermPos> list;

    /**
     * Index of the next position.
     */
    private int index;

    /**
     * Creates an iterator over positions.
     *
     * @param list The positions in ascending order
     */
    PositionCursor(final List<TermPos> list) {
        this.list = list;
        this.index = 0;
    }

    @Override
    public boolean hasNext() {
        return this.index < this.list.size();
    }

    @Override
    public TermPos next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException("No more positions");
        }
        final TermPos result = this.list.get(this.index);
        this.index += 1;
        return result;
    }

    @Override
    public void skipTo(final TermPos target) {
        final int found = Collections.binarySearch(this.list, target);
        int pos = found;
        if (found < 0) {
            pos = -found - 1;
        }
        this.index = Math.max(this.index, pos);
    }

    @Override
    public void close() {
        this.index = this.list.size();
    }
}
