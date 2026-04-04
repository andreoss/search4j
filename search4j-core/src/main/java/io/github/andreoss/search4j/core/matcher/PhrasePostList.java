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

    private PhrasePostList(final SelectSetup setup) {
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
        final List<PostList> terms = this.terms();
        final PositionList first = terms.get(0).readPositionList();
        boolean matched = false;
        boolean more = first.next();
        while (more && !matched) {
            final long base = first.position().value();
            long position = base;
            boolean within = true;
            for (int index = 1; index < terms.size() && within; ++index) {
                final long next = position + 1L;
                if (next > Integer.MAX_VALUE) {
                    within = false;
                } else {
                    final PositionList positions = terms.get(index)
                        .readPositionList();
                    within = positions.skipTo(new TermPos((int) next));
                    if (within) {
                        position = positions.position().value();
                        within = position - base + 1L <= this.window().value();
                    }
                }
            }
            matched = within;
            more = !matched && first.next();
        }
        return matched;
    }
}
