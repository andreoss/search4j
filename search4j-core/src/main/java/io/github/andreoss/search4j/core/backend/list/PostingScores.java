/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Frequencies and weights of a posting list.
 *
 * @since 0.1.0
 */
public interface PostingScores {

    /**
     * Number of documents in the list, an estimate for a combined list.
     *
     * @return Term frequency or its estimate
     */
    TermCount termFrequency();

    /**
     * Within-document frequency at the current document.
     *
     * <p>Lists without a single term raise an invalid-operation error.</p>
     *
     * @return Frequency of the term in the current document
     */
    default TermCount wdf() {
        throw new InvalidOperationError(
            "Within-document frequency is not meaningful for this posting list"
        );
    }

    /**
     * Weight of the current document.
     *
     * @param doc Statistics of the current document
     * @return Weight, zero when unweighted
     */
    double weight(DocStats doc);

    /**
     * Recomputes the highest weight the list can still return.
     *
     * @return Upper bound on any later weight
     */
    double recalcMaxWeight();

    /**
     * Number of leaf subqueries matching the current document.
     *
     * <p>Lists that do not track subqueries raise an invariant error.</p>
     *
     * @return Count of matching leaves
     */
    default TermCount countMatchingSubqueries() {
        throw new InvariantError("Matching subqueries are not counted here");
    }

    /**
     * Narrows the range of documents the list can return.
     *
     * @param whole Range already known, from one to the last identifier
     * @return Range no wider than the one given
     */
    default DocRange docIdRange(final DocRange whole) {
        return whole;
    }
}
