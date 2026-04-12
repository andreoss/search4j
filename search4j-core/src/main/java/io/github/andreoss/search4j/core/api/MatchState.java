/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state of one match run: the postings walked, the matches kept and
 * the collapse bookkeeping.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.NullAssignment")
final class MatchState {

    /**
     * Database the query runs over.
     */
    private final Shard shard;

    /**
     * Postings of the query.
     */
    private PostList list;

    /**
     * Matches kept so far.
     */
    private final List<MSet.Item> found;

    /**
     * Number of documents checked.
     */
    private int seen;

    /**
     * Number of documents added.
     */
    private int added;

    /**
     * One-based rank of the next match.
     */
    private int rank;

    /**
     * Collapse key of the previous match.
     */
    private String prev;

    /**
     * Size of the current collapse group.
     */
    private int group;

    /**
     * Creates the state of a run.
     *
     * @param shard Database the query runs over
     * @param list Postings of the query
     */
    MatchState(final Shard shard, final PostList list) {
        this.shard = shard;
        this.list = list;
        this.found = new ArrayList<>(8);
        this.seen = 0;
        this.added = 0;
        this.rank = 1;
        this.prev = null;
        this.group = 0;
    }

    PostList list() {
        return this.list;
    }

    /**
     * Moves to the next document, adopting the list to carry on with.
     */
    void advance() {
        this.list = this.list.next();
    }

    List<MSet.Item> found() {
        return this.found;
    }

    int seen() {
        return this.seen;
    }

    int added() {
        return this.added;
    }

    String prev() {
        return this.prev;
    }

    void prev(final String key) {
        this.prev = key;
    }

    int group() {
        return this.group;
    }

    void group(final int size) {
        this.group = size;
    }

    void check() {
        this.seen = this.seen + 1;
    }

    void drop() {
        this.added = this.added + 1;
    }

    double score(final DocId docid) {
        return this.list.weight(
            new DocStats(
                this.shard.docLength(docid),
                this.shard.uniqueTerms(docid),
                this.shard.wdfDocMax(docid)
            )
        );
    }

    void add(final DocId docid, final double score) {
        this.found.add(
            new MSetItemImpl(docid, score, this.rank, this.shard)
        );
        this.rank = this.rank + 1;
    }

    void rank() {
        for (int idx = 0; idx < this.found.size(); idx = idx + 1) {
            ((MSetItemImpl) this.found.get(idx)).setRank(idx + 1);
        }
    }
}
