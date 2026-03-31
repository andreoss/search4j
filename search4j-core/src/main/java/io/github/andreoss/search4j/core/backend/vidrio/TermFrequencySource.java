/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;

/**
 * Source of the frequencies of a term, as stored or as changed so far.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface TermFrequencySource {

    /**
     * Frequencies of a term.
     *
     * @param term Term text
     * @return Frequencies, {@link TermFrequencies#NONE} for an unknown term
     */
    TermFrequencies frequencies(String term);
}
