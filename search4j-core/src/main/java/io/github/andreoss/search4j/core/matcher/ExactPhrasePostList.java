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
 * Ordered positional match at adjacent term positions.
 *
 * @since 0.1.0
 */
public final class ExactPhrasePostList extends SelectPostList {

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

    private ExactPhrasePostList(final SelectSetup setup) {
        super(setup);
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
        final List<PostList> terms = this.terms();
        final PositionList first = terms.get(0).readPositionList();
        boolean matched = first.next();
        while (matched && !ExactPhrasePostList.matchesFrom(first, terms)) {
            matched = first.next();
        }
        return matched;
    }

    private static boolean matchesFrom(
        final PositionList first, final List<PostList> terms
    ) {
        final long base = first.position().value();
        boolean adjacent = true;
        int index = 1;
        while (index < terms.size() && adjacent) {
            final long required = base + index;
            if (required > Integer.MAX_VALUE) {
                adjacent = false;
            } else {
                final PositionList positions = terms.get(index)
                    .readPositionList();
                adjacent = positions.skipTo(new TermPos((int) required));
                if (adjacent) {
                    adjacent = positions.position().value() == required;
                }
            }
            ++index;
        }
        return adjacent;
    }
}
