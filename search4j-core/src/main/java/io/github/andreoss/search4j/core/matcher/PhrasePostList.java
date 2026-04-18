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
 * Ordered positional match contained in a term-position window.
 *
 * @since 0.1.0
 */
public final class PhrasePostList extends SelectPostList {

    /**
     * Outcome of a full match.
     */
    private static final long ACCEPT = -1L;

    /**
     * Outcome of a match still in progress.
     */
    private static final long MORE = -3L;

    /**
     * Number of position lists read for the document tested.
     */
    private int read;

    /**
     * Position of the last term matched.
     */
    private long pos;

    /**
     * Creates an ordered positional filter.
     *
     * @param candidates Stream containing every required term
     * @param span Maximum inclusive span of a phrase match
     * @param terms Terms in query order
     */
    public PhrasePostList(
        final PostList candidates,
        final TermPos span,
        final List<? extends PostList> terms
    ) {
        this(SelectSetup.positional(
            SelectSetup.simple(candidates, terms, 3), span
        ));
    }

    PhrasePostList(final SelectSetup setup) {
        super(setup);
    }

    @Override
    public TermCount wdf() {
        return this.minimumWdf();
    }

    @Override
    public String description() {
        return String.format(
            "(Phrase %s %s)", this.window(), this.source().description()
        );
    }

    @Override
    boolean testDoc() {
        final PositionList[] lists = new PositionList[this.terms().size()];
        lists[0] = this.terms().get(0).readPositionList();
        this.read = 0;
        final long window = this.window().value() & Positions.MASK;
        boolean result = false;
        boolean more = lists[0].next();
        while (more && !result) {
            final long bound = this.attempt(lists, window);
            result = bound == PhrasePostList.ACCEPT;
            more = bound >= 0L && Positions.skip(lists[0], bound - window);
        }
        return result;
    }

    private long attempt(final PositionList[] lists, final long window) {
        final long base = Positions.at(lists[0]);
        this.pos = base;
        int index = 0;
        long result = PhrasePostList.MORE;
        while (result == PhrasePostList.MORE) {
            index = index + 1;
            if (index == lists.length) {
                result = PhrasePostList.ACCEPT;
            } else {
                result = this.step(lists, index, base, window);
            }
        }
        return result;
    }

    private long step(
        final PositionList[] lists, final int index, final long base, final long window
    ) {
        if (index > this.read) {
            this.read = index;
            lists[index] = this.terms().get(index).readPositionList();
        }
        long result = Long.MIN_VALUE;
        if (Positions.skip(lists[index], this.pos + 1L)) {
            this.pos = Positions.at(lists[index]);
            result = this.pos + lists.length - index & Positions.MASK;
            if ((result - base & Positions.MASK) <= window) {
                result = PhrasePostList.MORE;
            }
        }
        return result;
    }
}
