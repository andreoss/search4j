/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.CollectionStats;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Shard, collection statistics and recalculation callback shared by the
 * contexts that build a posting list tree.
 *
 * @param shard Shard searched
 * @param stats Collection statistics of the search
 * @param recalc Callback when maximum weights change
 * @since 0.10.0
 */
record Matching(Shard shard, CollectionStats stats, Runnable recalc) {

    TermCount dbsize() {
        return this.shard.docCount();
    }

    DocStats docStats(final DocId docid) {
        return new DocStats(
            this.shard.docLength(docid), this.shard.uniqueTerms(docid),
            this.shard.wdfDocMax(docid)
        );
    }

    long size() {
        return this.stats.size().value();
    }

    long rset() {
        return this.stats.rset().value();
    }

    long total() {
        return this.stats.total().value();
    }

    TermFreqs everything() {
        return new TermFreqs(
            this.size(), this.rset(), Math.min(this.total(), 0xFFFF_FFFFL)
        );
    }
}
