/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;

/**
 * Term-independent weight contribution added to every matching document.
 *
 * <p>The reference supplies this from its weighting scheme; the matcher only
 * needs the sum and its bound, so that surface is a port here and the full
 * scheme belongs to the weighting framework.</p>
 *
 * @since 0.1.0
 */
public interface ExtraWeight {

    /**
     * Contribution for one document.
     *
     * @param doc Statistics of the document
     * @return Contribution, never above {@link #maxExtra()}
     */
    double sumExtra(DocStats doc);

    /**
     * Upper bound on any contribution.
     *
     * @return Highest possible contribution
     */
    double maxExtra();
}
