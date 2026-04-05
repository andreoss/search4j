/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Keeps documents a match decider accepts.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class DeciderPostList extends SelectPostList {

    /**
     * Database supplying the candidate documents.
     */
    private final Database database;

    /**
     * Decides whether a document is accepted.
     */
    private final MatchDecider decider;

    private DeciderPostList(
        final SelectSetup setup, final Database source,
        final MatchDecider accept
    ) {
        super(setup);
        this.database = source;
        this.decider = accept;
    }

    /**
     * Creates the decider filter.
     *
     * @param candidates Candidate stream
     * @param database Database supplying candidate documents
     * @param decider Decides whether a document is accepted
     * @return Decider filter
     */
    public static DeciderPostList of(
        final PostList candidates, final Database database,
        final MatchDecider decider
    ) {
        if (database == null) {
            throw new InvalidArgumentError("A database is required");
        }
        if (decider == null) {
            throw new InvalidArgumentError("A decider is required");
        }
        return new DeciderPostList(
            SelectSetup.filter(candidates, 2), database, decider
        );
    }

    @Override
    public TermCount wdf() {
        return this.source().wdf();
    }

    @Override
    public String description() {
        return String.format(
            "DeciderPostList(%s)", this.source().description()
        );
    }

    @Override
    boolean testDoc() {
        return this.decider.accept(this.database.document(this.docId()));
    }
}
