/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Statistics of one term in a database.
 *
 * @param frequency Number of documents containing the term
 * @param collection Total number of occurrences of the term
 * @param maxWdf Highest within-document frequency of the term
 * @since 0.1.0
 */
public record TermInfo(
    TermCount frequency, TermCount collection, TermCount maxWdf
) {

    /**
     * Human-readable description.
     *
     * @return Description naming the three statistics
     */
    public String description() {
        return String.format(
            "TermInfo(termfreq=%d, collfreq=%d, wdf_upper_bound=%d)",
            this.frequency.value(), this.collection.value(),
            this.maxWdf.value()
        );
    }
}
