/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Weighting scheme scoring a synonym group as one term.
 *
 * <p>The reference supplies this from its weighting scheme; the matcher only
 * needs the summed within-document frequency, the document statistics, the
 * maximum contribution and whether the within-document frequency and its
 * maximum are wanted.</p>
 *
 * @since 0.1.0
 */
public interface SynonymWeight {

    /**
     * Contribution of a synonym group.
     *
     * @param wdf Summed within-document frequency of the group
     * @param doc Statistics of the document
     * @return Contribution, never above {@link #maxPart()}
     */
    double sumPart(TermCount wdf, DocStats doc);

    /**
     * Upper bound on any contribution.
     *
     * @return Highest possible contribution
     */
    double maxPart();

    /**
     * Whether the scheme needs the within-document frequency.
     *
     * @return True when wanted
     */
    boolean needsWdf();

    /**
     * Whether the scheme needs the document length to clamp the frequency.
     *
     * @return True when clamping is needed
     */
    boolean needsDocLength();

    /**
     * Whether the scheme needs the highest within-document frequency of the
     * document, which the group takes as the document length.
     *
     * @return True when wanted
     * @since 0.10.0
     */
    default boolean needsWdfDocMax() {
        return true;
    }
}
