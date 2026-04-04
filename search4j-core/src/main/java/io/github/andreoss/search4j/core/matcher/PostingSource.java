/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * External source of documents and their weights.
 *
 * <p>Mirrors the reference posting source surface needed by the external
 * posting list: a reset against a database, forward movement with a weight
 * hint, document order, weight, its bound and term-frequency bounds.</p>
 *
 * @since 0.1.0
 */
public interface PostingSource {

    /**
     * Prepares the source against a database.
     *
     * @param database Database read
     * @param shard Shard index read, zero for a single shard
     */
    void reset(Database database, int shard);

    /**
     * Moves to the next document.
     *
     * @param least Lowest weight still wanted, a hint only
     */
    void next(double least);

    /**
     * Moves to the first document at or after a target, never backwards.
     *
     * @param target Lowest document wanted
     * @param least Lowest weight still wanted, a hint only
     */
    default void skipTo(final DocId target, final double least) {
        while (!this.atEnd() && this.docId().compareTo(target) < 0) {
            this.next(least);
        }
    }

    /**
     * Tests whether the source is exhausted.
     *
     * @return True once moved past the last document
     */
    boolean atEnd();

    /**
     * Current document.
     *
     * @return Document identifier
     */
    DocId docId();

    /**
     * Weight of the current document.
     *
     * @return Weight, zero when unweighted
     */
    double weight();

    /**
     * Upper bound on any later weight.
     *
     * @return Highest possible weight
     */
    double maxWeight();

    /**
     * Lowest possible number of matching documents.
     *
     * @return Lower frequency bound
     */
    TermCount termFrequencyLower();

    /**
     * Best estimate of the number of matching documents.
     *
     * @return Estimated frequency
     */
    TermCount termFrequencyEstimate();

    /**
     * Highest possible number of matching documents.
     *
     * @return Upper frequency bound
     */
    TermCount termFrequencyUpper();
}
