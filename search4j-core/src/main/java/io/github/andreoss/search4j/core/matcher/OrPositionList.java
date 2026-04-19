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
     * Current position of each live source, empty before the first move.
     */
    private long[] positions;

    /**
     * Current merged position.
     */
    private long current;

    /**
     * Creates a merge over independent source position lists.
     *
     * @param sources Position lists to merge
     */
    public OrPositionList(final List<? extends PositionList> sources) {
        this(OrPositionSetup.fresh(sources));
    }

    private OrPositionList(final OrPositionSetup setup) {
        this(setup.lists(), new long[0]);
    }

    private OrPositionList(final List<PositionList> lists, final long... positions) {
        this.lists = lists;
        this.positions = positions;
    }

    @Override
    public TermCount approxSize() {
        long total = 0L;
        for (final PositionList list : this.lists) {
            total += list.approxSize().value();
        }
        return TermCount.wrap(total);
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
        if (this.positions.length == 0) {
            throw new InvalidOperationError("Position list is not on a position");
        }
        return new TermPos(this.current);
    }

    @Override
    public boolean next() {
        return this.merge(this.current, false);
    }

    @Override
    public boolean skipTo(final TermPos target) {
        Objects.requireNonNull(target, "target");
        final long wanted = target.value() & Positions.MASK;
        boolean result = true;
        if (this.positions.length == 0 || wanted > this.current) {
            result = this.merge(wanted, true);
        }
        return result;
    }

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

    private boolean merge(final long bound, final boolean skip) {
        final boolean first = this.positions.length == 0;
        if (first) {
            this.positions = new long[this.lists.size()];
        }
        this.current = Positions.MASK;
        int kept = 0;
        for (int index = 0; index < this.lists.size(); ++index) {
            final long pos = this.moved(index, first, bound, skip);
            if (pos >= 0L) {
                kept = this.keep(kept, index, pos);
            }
        }
        this.truncate(kept);
        return kept != 0;
    }

    private long moved(
        final int index, final boolean first, final long bound, final boolean skip
    ) {
        final PositionList list = this.lists.get(index);
        long result = this.positions[index];
        final boolean due;
        final boolean live;
        if (skip) {
            due = first || bound > result;
            live = !due || Positions.skip(list, bound);
        } else {
            due = first || result <= bound;
            live = !due || list.next();
        }
        if (due) {
            result = Positions.at(list, live) - Positions.flag(live);
        }
        return result;
    }

    private int keep(final int kept, final int index, final long pos) {
        this.current = Math.min(this.current, pos);
        this.positions[kept] = pos;
        this.lists.set(kept, this.lists.get(index));
        return kept + 1;
    }

    private void truncate(final int kept) {
        while (this.lists.size() > kept) {
            this.lists.remove(this.lists.size() - 1);
        }
    }
}
