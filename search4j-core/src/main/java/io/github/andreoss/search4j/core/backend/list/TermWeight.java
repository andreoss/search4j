/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Port to a weighting scheme scoring the postings of one term.
 *
 * @since 0.1.0
 */
public interface TermWeight {

    /**
     * Weight contribution of one posting.
     *
     * @param wdf Within-document frequency of the term
     * @param doc Statistics of the document posted
     * @return Contribution, never above {@link #maxPart()}
     */
    double sumPart(TermCount wdf, DocStats doc);

    /**
     * Upper bound on any contribution from this term.
     *
     * @return Highest possible contribution
     */
    double maxPart();

    /**
     * Whether a contribution depends on the document length, so that a
     * frequency summed over several terms is clamped to it.
     *
     * @return True unless the scheme ignores the document length
     * @since 0.10.0
     */
    default boolean needsDocLength() {
        return true;
    }

    /**
     * Whether a contribution depends on the within-document frequency, so
     * that a synonym group needs the frequency summed over its terms.
     *
     * @return True unless the scheme ignores the frequency
     * @since 0.10.0
     */
    default boolean needsWdf() {
        return true;
    }

    /**
     * Whether a contribution depends on the highest within-document
     * frequency of the document, which a synonym group takes as the
     * document length.
     *
     * @return True when the scheme uses that frequency
     * @since 0.10.0
     */
    default boolean needsWdfDocMax() {
        return false;
    }
}
