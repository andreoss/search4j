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
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;

/**
 * Unordered positional match contained in a term-position window.
 *
 * <p>Terms are read from the lowest within-document frequency, an order kept
 * from one document to the next; the position lists form a heap with the
 * lowest position on top.</p>
 *
 * @since 0.1.0
 */
public final class NearPostList extends SelectPostList {

    /**
     * Position lists of the document tested.
     */
    private PositionList[] heap;

    /**
     * Highest position reached.
     */
    private long last;

    /**
     * Number of position lists started.
     */
    private int end;

    /**
     * Window of positions.
     */
    private long span;

    /**
     * Creates an unordered positional filter.
     *
     * @param candidates Stream containing every required term
     * @param span Maximum inclusive span of a near match
     * @param terms Terms in query order
     */
    public NearPostList(
        final PostList candidates,
        final TermPos span,
        final List<? extends PostList> terms
    ) {
        this(SelectSetup.positional(
            SelectSetup.simple(candidates, terms, 2), span
        ));
    }

    NearPostList(final SelectSetup setup) {
        super(setup);
    }

    @Override
    public TermCount wdf() {
        return this.minimumWdf();
    }

    @Override
    public String description() {
        return String.format(
            "(Near %s %s)", this.window(), this.source().description()
        );
    }

    @Override
    boolean testDoc() {
        final List<PostList> terms = this.terms();
        Positions.sortByWdf(terms);
        this.heap = new PositionList[terms.size()];
        this.heap[0] = terms.get(0).readPositionList();
        this.span = this.window().value() & Positions.MASK;
        boolean result = false;
        if (this.heap[0].next()) {
            this.last = Positions.at(this.heap[0]);
            this.end = 1;
            int state = 0;
            while (state == 0) {
                if ((this.last - Positions.at(this.heap[0]) & Positions.MASK) < this.span) {
                    state = this.grow();
                } else {
                    state = this.slide();
                }
            }
            result = state > 0;
        }
        return result;
    }

    private int grow() {
        final int state;
        if (this.end == this.heap.length) {
            state = this.distinct();
        } else {
            state = this.add();
        }
        return state;
    }

    private int add() {
        final PositionList list = this.terms().get(this.end).readPositionList();
        final boolean placed;
        if (this.last < this.span) {
            placed = list.next();
        } else {
            placed = Positions.skip(list, this.last - this.span + 1L);
        }
        int state = -1;
        if (placed) {
            this.last = Math.max(this.last, Positions.at(list));
            this.heap[this.end] = list;
            this.end = this.end + 1;
            Positions.push(this.heap, this.end, NearPostList::later);
            state = 0;
        }
        return state;
    }

    private int slide() {
        int state = -1;
        if (Positions.skip(this.heap[0], this.last - this.span + 1L)) {
            this.last = Math.max(this.last, Positions.at(this.heap[0]));
            Positions.replace(this.heap, this.end, NearPostList::later);
            state = 0;
        }
        return state;
    }

    private int distinct() {
        long pos = Positions.at(this.heap[0]);
        Positions.pop(this.heap, this.end, NearPostList::later);
        int index = this.end - 1;
        int state = 2;
        while (state == 2) {
            if (Positions.at(this.heap[0]) == pos) {
                state = this.bump(index);
            } else {
                pos = Positions.at(this.heap[0]);
                Positions.pop(this.heap, index, NearPostList::later);
                index = index - 1;
                if (index == 0) {
                    state = 1;
                }
            }
        }
        if (state == 0) {
            Positions.make(this.heap, this.end, NearPostList::later);
        }
        return state;
    }

    private int bump(final int index) {
        int state = -1;
        if (this.heap[0].next()) {
            final long moved = Positions.at(this.heap[0]);
            if ((moved - Positions.at(this.heap[this.end - 1]) & Positions.MASK) >= this.span) {
                this.last = moved;
                state = 0;
            } else {
                Positions.replace(this.heap, index, NearPostList::later);
                state = 2;
            }
        }
        return state;
    }

    private static boolean later(final PositionList left, final PositionList right) {
        return Positions.at(left) > Positions.at(right);
    }
}
