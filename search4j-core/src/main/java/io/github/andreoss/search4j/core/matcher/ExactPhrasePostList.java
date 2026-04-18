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
import java.util.ArrayList;
import java.util.List;

/**
 * Ordered positional match at adjacent term positions.
 *
 * <p>Terms are checked from the lowest within-document frequency, an order
 * kept from one document to the next.</p>
 *
 * @since 0.1.0
 */
public final class ExactPhrasePostList extends SelectPostList {

    /**
     * Query offsets of the terms in checking order, empty before the first
     * document is tested.
     */
    private final List<Integer> order;

    /**
     * Creates an exact positional filter.
     *
     * @param candidates Stream containing every required term
     * @param terms Terms in query order
     */
    public ExactPhrasePostList(
        final PostList candidates,
        final List<? extends PostList> terms
    ) {
        this(SelectSetup.simple(candidates, terms, 4));
    }

    ExactPhrasePostList(final SelectSetup setup) {
        super(setup);
        this.order = new ArrayList<>(4);
    }

    @Override
    public TermCount wdf() {
        return this.minimumWdf();
    }

    @Override
    public String description() {
        return String.format("(ExactPhrase %s)", this.source().description());
    }

    @Override
    boolean testDoc() {
        this.sort();
        final PositionList[] lists = new PositionList[this.order.size()];
        lists[0] = this.read(0);
        boolean more = Positions.skip(lists[0], this.order.get(0));
        if (more) {
            lists[1] = this.read(1);
            more = lists[0].approxSize().compareTo(lists[1].approxSize()) <= 0
                || this.swap(lists);
        }
        return more && this.search(lists);
    }

    private boolean swap(final PositionList... lists) {
        final boolean result = Positions.skip(lists[1], this.order.get(1));
        if (result) {
            final PositionList list = lists[0];
            lists[0] = lists[1];
            lists[1] = list;
            this.order.set(0, this.order.set(1, this.order.get(0)));
        }
        return result;
    }

    private boolean search(final PositionList... lists) {
        int read = 1;
        final long first = this.order.get(0);
        long base = Positions.at(lists[0]) - first & Positions.MASK;
        int index = 1;
        boolean result = false;
        boolean more = true;
        while (more && !result) {
            if (index > read) {
                read = index;
                lists[index] = this.read(index);
            }
            final long offset = this.order.get(index);
            final long required = base + offset & Positions.MASK;
            more = Positions.skip(lists[index], required);
            final long got = Positions.at(lists[index], more);
            if (more && got == required) {
                index = index + 1;
                result = index == this.order.size();
            } else if (more) {
                more = Positions.skip(lists[0], got - offset + first);
                base = Positions.at(lists[0], more) - first & Positions.MASK;
                index = 1;
            }
        }
        return result;
    }

    private PositionList read(final int index) {
        return this.terms().get(this.order.get(index)).readPositionList();
    }

    private void sort() {
        final List<PostList> terms = this.terms();
        if (this.order.isEmpty()) {
            for (int index = 0; index < terms.size(); ++index) {
                this.order.add(index);
            }
        }
        Positions.sortByWdf(this.order, terms);
    }
}
