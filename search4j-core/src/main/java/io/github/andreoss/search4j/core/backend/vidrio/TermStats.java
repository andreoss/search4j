/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Statistics of one term read from the start of its posting list.
 *
 * @param freqs Term and collection frequencies
 * @param wdfUpperBound Upper bound on the within-document frequency
 * @since 0.1.0
 */
public record TermStats(TermFrequencies freqs, TermCount wdfUpperBound) {

    /**
     * Statistics of a term indexing nothing.
     */
    public static final TermStats NONE =
        new TermStats(TermFrequencies.NONE, TermCount.ZERO);
}
