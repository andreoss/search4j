/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Prepared state for positional posting list filters.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.Objects;

/**
 * Validated source, children, estimate and optional position window.
 *
 * @param source Candidate stream
 * @param terms Positional children in query order
 * @param frequency Estimated number of selected documents
 * @param window Position window, null for an exact phrase
 * @since 0.1.0
 */
record SelectSetup(
    PostList source,
    List<PostList> terms,
    TermCount frequency,
    TermPos window
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
            null
        );
    }

    static SelectSetup positional(
        final SelectSetup setup, final TermPos window
    ) {
        return new SelectSetup(
            setup.source(), setup.terms(), setup.frequency(),
            Objects.requireNonNull(window, "window")
        );
    }

    private static SelectSetup prepare(
        final PostList source,
        final List<? extends PostList> terms,
        final int divisor
    ) {
        final PostList checked = Objects.requireNonNull(source, "source");
        final List<PostList> children = List.copyOf(
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
            null
        );
    }
}
