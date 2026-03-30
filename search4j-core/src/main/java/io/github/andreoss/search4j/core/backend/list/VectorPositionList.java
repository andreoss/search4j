/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Collections;
import java.util.List;

/**
 * Position list over an ascending list held in memory.
 *
 * @since 0.1.0
 */
public final class VectorPositionList implements PositionList {

    /**
     * The positions in ascending order.
     */
    private final List<TermPos> list;

    /**
     * Index of the current position, minus one before the start.
     */
    private int index;

    /**
     * Creates a list over a copy of ascending positions.
     *
     * @param positions Ascending positions without repeats
     */
    public VectorPositionList(final List<TermPos> positions) {
        this(List.copyOf(positions), -1);
    }

    /**
     * Creates a list at an index.
     *
     * @param list Ascending positions without repeats
     * @param index Index of the current position, minus one before the start
     */
    private VectorPositionList(final List<TermPos> list, final int index) {
        this.list = list;
        this.index = index;
    }

    @Override
    public TermCount approxSize() {
        return new TermCount(this.list.size());
    }

    @Override
    public TermPos back() {
        TermPos result = TermPos.FIRST;
        if (!this.list.isEmpty()) {
            result = this.list.get(this.list.size() - 1);
        }
        return result;
    }

    @Override
    public TermPos position() {
        if (this.index < 0 || this.index >= this.list.size()) {
            throw new InvalidOperationError("Position list is not on a position");
        }
        return this.list.get(this.index);
    }

    @Override
    public boolean next() {
        if (this.index < this.list.size()) {
            this.index += 1;
        }
        return this.index < this.list.size();
    }

    @Override
    public boolean skipTo(final TermPos target) {
        final int found = Collections.binarySearch(this.list, target);
        int pos = found;
        if (found < 0) {
            pos = -found - 1;
        }
        this.index = Math.max(this.index, pos);
        return this.index < this.list.size();
    }
}
