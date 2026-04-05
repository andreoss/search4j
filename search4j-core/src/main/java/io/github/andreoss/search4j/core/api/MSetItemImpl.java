/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.types.DocId;

/**
 * A match in a match set, tied to its document.
 *
 * @since 0.1.0
 */
final class MSetItemImpl implements MSet.Item {

    /**
     * Document identifier of the match.
     */
    private final DocId docid;

    /**
     * Weight of the match under the weighting scheme.
     */
    private final double score;

    /**
     * Database the document belongs to.
     */
    private final Shard shard;

    /**
     * One-based rank of the match.
     */
    private int position;

    /**
     * Creates a match.
     *
     * @param docid Document identifier
     * @param score Weight of the match
     * @param position One-based rank
     * @param shard Database the document belongs to
     */
    MSetItemImpl(
        final DocId docid, final double score, final int position,
        final Shard shard
    ) {
        this.docid = docid;
        this.score = score;
        this.position = position;
        this.shard = shard;
    }

    @Override
    public DocId docId() {
        return this.docid;
    }

    @Override
    public double weight() {
        return this.score;
    }

    @Override
    public int rank() {
        return this.position;
    }

    @Override
    public Document document() {
        return this.shard.openDocument(this.docid, false).orElseThrow(
            () -> new IllegalStateException(
                String.format("Document %s not found", this.docid)
            )
        );
    }

    void setRank(final int rank) {
        this.position = rank;
    }
}
