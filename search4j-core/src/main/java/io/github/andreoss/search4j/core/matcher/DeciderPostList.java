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
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Keeps documents a match decider accepts.
 *
 * @since 0.1.0
 */
public final class DeciderPostList extends SelectPostList {

    /**
     * Database supplying the candidate documents.
     */
    private final Database database;

    /**
     * Decides whether a document is accepted.
     */
    private final MatchDecider decider;

    /**
     * Creates the decider filter over prepared candidates.
     *
     * @param candidates Candidate stream
     * @param source Database supplying candidate documents
     * @param accept Decides whether a document is accepted
     */
    public DeciderPostList(
        final PostList candidates, final Database source,
        final MatchDecider accept
    ) {
        super(SelectSetup.filter(candidates, 2));
        if (source == null) {
            throw new NullPointerException("database");
        }
        if (accept == null) {
            throw new NullPointerException("decider");
        }
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
        return new DeciderPostList(candidates, database, decider);
    }

    @Override
    boolean testDoc() {
        return this.decider.accept(this.database.document(this.docId()));
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
}
