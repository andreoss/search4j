/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.Map;

/**
 * One expansion pass over the terms of the matching documents.
 *
 * @since 0.1.0
 */
final class ExpandRun {

    /**
     * Document frequencies collected in the matching set.
     */
    private final Map<String, TermCount> dfreqs;

    /**
     * Number of matching documents.
     */
    private final int matching;

    /**
     * Expansion weight scheme.
     */
    private final ExpandWeight weight;

    /**
     * Filter of expansion terms, null for none.
     */
    private ExpandDecider chooser;

    /**
     * Terms produced so far.
     */
    private List<ESet.Item> items;

    /**
     * Creates an expansion pass.
     *
     * @param dfreqs Document frequencies collected in the matching set
     * @param matching Number of matching documents
     * @param weight Expansion weight scheme
     */
    ExpandRun(
        final Map<String, TermCount> dfreqs, final int matching,
        final ExpandWeight weight
    ) {
        this.dfreqs = dfreqs;
        this.matching = matching;
        this.weight = weight;
    }

    void chooser(final ExpandDecider filter) {
        this.chooser = filter;
    }

    void items(final List<ESet.Item> found) {
        this.items = found;
    }

    List<ESet.Item> items() {
        return this.items;
    }

    void add(final String term, final TermCount freq) {
        if (this.chooser == null || this.chooser.decide(term, freq)) {
            this.store(term, freq);
        }
    }

    private void store(final String term, final TermCount freq) {
        final double score = this.weight.weight(
            term,
            new ExpandStats(
                freq, this.dfreqs.getOrDefault(term, TermCount.ZERO),
                new TermCount(this.matching)
            )
        );
        if (score > 0.0) {
            this.items.add(new ESetItemImpl(term, freq, score));
        }
    }
}
