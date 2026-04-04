/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sorted unique union of position lists.
 *
 * @since 0.1.0
 */
public final class OrPositionList implements PositionList {

    /**
     * Source position lists that still have values.
     */
    private final List<PositionList> lists;

    /**
     * Current position from each live source.
     */
    private final List<TermPos> positions;

    /**
     * Current merged position, absent before the first move and after the end.
     */
    private TermPos current;

    /**
     * Whether source lists have received their first move.
     */
    private boolean started;

    /**
     * Creates a merge over independent source position lists.
     *
     * @param sources Position lists to merge
     */
    public OrPositionList(final List<? extends PositionList> sources) {
        this(OrPositionSetup.fresh(sources));
    }

    private OrPositionList(final OrPositionSetup setup) {
        this(setup.lists(), setup.positions());
    }

    private OrPositionList(
        final List<PositionList> sources,
        final List<TermPos> positions
    ) {
        this.lists = sources;
        this.positions = positions;
        this.started = false;
    }

    @Override
    public TermCount approxSize() {
        long total = 0L;
        for (final PositionList list : this.lists) {
            total += list.approxSize().value();
        }
        return new TermCount((int) Math.min(total, Integer.MAX_VALUE));
    }

    @Override
    public TermPos back() {
        TermPos last = TermPos.FIRST;
        for (final PositionList list : this.lists) {
            final TermPos position = list.back();
            if (position.compareTo(last) > 0) {
                last = position;
            }
        }
        return last;
    }

    @Override
    public TermPos position() {
        if (this.current == null) {
            throw new InvalidOperationError(
                "Position list is not on a position"
            );
        }
        return this.current;
    }

    @Override
    public boolean next() {
        final boolean initial = !this.started;
        if (initial) {
            this.started = true;
            this.beginNext();
        }
        if (!initial && this.current != null) {
            this.advanceNext();
        }
        return this.selectCurrent();
    }

    @Override
    public boolean skipTo(final TermPos target) {
        Objects.requireNonNull(target, "target");
        final boolean move = this.current == null
            || target.compareTo(this.current) > 0;
        if (move) {
            this.started = true;
            if (this.current == null) {
                this.beginTo(target);
            }
            if (this.current != null) {
                this.advanceTo(target);
            }
        }
        return this.selectCurrent();
    }

    /**
     * Gathers positions from the current posting list document.
     *
     * @param source Posting list supplying the matching leaves
     * @return Sole position list or a merged list
     */
    static PositionList gather(final PostList source) {
        Objects.requireNonNull(source, "source");
        final List<PositionList> gathered = new ArrayList<>(0);
        source.gatherPositionLists(gathered::add);
        PositionList result = new OrPositionList(gathered);
        if (gathered.size() == 1) {
            result = gathered.get(0);
        }
        return result;
    }

    private void beginNext() {
        int index = 0;
        while (index < this.lists.size()) {
            final PositionList list = this.lists.get(index);
            if (list.next()) {
                this.positions.add(list.position());
                ++index;
            } else {
                this.lists.remove(index);
            }
        }
    }

    private void beginTo(final TermPos target) {
        int index = 0;
        while (index < this.lists.size()) {
            final PositionList list = this.lists.get(index);
            if (list.skipTo(target)) {
                this.positions.add(list.position());
                ++index;
            } else {
                this.lists.remove(index);
            }
        }
    }

    private void advanceNext() {
        int index = 0;
        while (index < this.lists.size()) {
            if (this.positions.get(index).equals(this.current)) {
                final PositionList list = this.lists.get(index);
                if (list.next()) {
                    this.positions.set(index, list.position());
                    ++index;
                } else {
                    this.lists.remove(index);
                    this.positions.remove(index);
                }
            } else {
                ++index;
            }
        }
    }

    private void advanceTo(final TermPos target) {
        int index = 0;
        while (index < this.lists.size()) {
            if (this.positions.get(index).compareTo(target) < 0) {
                final PositionList list = this.lists.get(index);
                if (list.skipTo(target)) {
                    this.positions.set(index, list.position());
                    ++index;
                } else {
                    this.lists.remove(index);
                    this.positions.remove(index);
                }
            } else {
                ++index;
            }
        }
    }

    private boolean selectCurrent() {
        TermPos minimum = null;
        for (final TermPos position : this.positions) {
            if (minimum == null || position.compareTo(minimum) < 0) {
                minimum = position;
            }
        }
        this.current = minimum;
        return minimum != null;
    }
}
