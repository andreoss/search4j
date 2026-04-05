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
 * An expansion term of an expansion set.
 *
 * @since 0.1.0
 */
final class ESetItemImpl implements ESet.Item {

    /**
     * Term text.
     */
    private final String text;

    /**
     * Estimated term frequency in the database.
     */
    private final TermCount frequency;

    /**
     * Expansion weight of the term.
     */
    private final double score;

    /**
     * Creates an expansion term.
     *
     * @param text Term text
     * @param frequency Estimated term frequency in the database
     * @param score Expansion weight of the term
     */
    ESetItemImpl(
        final String text, final TermCount frequency, final double score
    ) {
        this.text = text;
        this.frequency = frequency;
        this.score = score;
    }

    @Override
    public String term() {
        return this.text;
    }

    @Override
    public TermCount estimatedTermFrequency() {
        return this.frequency;
    }

    @Override
    public double weight() {
        return this.score;
    }
}
