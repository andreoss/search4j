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
}
