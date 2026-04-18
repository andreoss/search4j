/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Prepared state of a filtering posting list.
 *
 * @param source Candidate stream
 * @param terms Positional children in query order
 * @param frequency Estimated number of selected documents
 * @param window Position window, null for an exact phrase
 * @param stats Statistics of a document, null when candidates are not weighed
 * @since 0.1.0
 */
record SelectSetup(
    PostList source,
    List<PostList> terms,
    TermCount frequency,
    TermPos window,
    Function<DocId, DocStats> stats
) {
    static SelectSetup simple(
        final PostList source,
        final List<? extends PostList> terms,
        final int divisor
    ) {
        return SelectSetup.prepare(source, terms, divisor);
    }

    static SelectSetup filter(final PostList source, final int divisor) {
        final PostList checked = Objects.requireNonNull(source, "source");
        if (divisor < 1) {
            throw new InvalidArgumentError("Estimate divisor must be positive");
        }
        return new SelectSetup(
            checked,
            List.of(),
            new TermCount(checked.termFrequency().value() / divisor),
            null,
            null
        );
    }

    static SelectSetup positional(
        final SelectSetup setup, final TermPos window
    ) {
        return new SelectSetup(
            setup.source(), setup.terms(), setup.frequency(),
            Objects.requireNonNull(window, "window"), setup.stats()
        );
    }

    SelectSetup weighed(final Function<DocId, DocStats> fetch) {
        return new SelectSetup(
            this.source, this.terms, this.frequency, this.window,
            Objects.requireNonNull(fetch, "stats")
        );
    }

    private static SelectSetup prepare(
        final PostList source,
        final List<? extends PostList> terms,
        final int divisor
    ) {
        final PostList checked = Objects.requireNonNull(source, "source");
        final List<PostList> children = new ArrayList<>(
            Objects.requireNonNull(terms, "terms")
        );
        if (children.size() < 2) {
            throw new InvalidArgumentError(
                "Positional operators need at least two children"
            );
        }
        if (divisor < 1) {
            throw new InvalidArgumentError("Estimate divisor must be positive");
        }
        return new SelectSetup(
            checked,
            children,
            new TermCount(checked.termFrequency().value() / divisor),
            null,
            null
        );
    }
}
