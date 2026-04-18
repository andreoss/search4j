/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

/**
 * Estimated term, relevant-set and collection frequencies of a subquery,
 * gathered so that a synonym group is weighted as one term.
 *
 * @since 0.10.0
 */
final class TermFreqs {

    /**
     * Mask of an unsigned 32-bit count.
     */
    private static final long UNSIGNED = 0xFFFF_FFFFL;

    /**
     * Number of documents indexed.
     */
    private long term;

    /**
     * Number of relevant documents indexed.
     */
    private long rel;

    /**
     * Number of occurrences in the collection.
     */
    private long coll;

    TermFreqs() {
        this(0L, 0L, 0L);
    }

    TermFreqs(final long term, final long rel, final long coll) {
        this.term = term;
        this.rel = rel;
        this.coll = coll;
    }

    long term() {
        return this.term;
    }

    long rel() {
        return this.rel;
    }

    long coll() {
        return this.coll;
    }

    void assign(final long docs, final long relevant, final long occurrences) {
        this.term = docs & TermFreqs.UNSIGNED;
        this.rel = relevant & TermFreqs.UNSIGNED;
        this.coll = occurrences & TermFreqs.UNSIGNED;
    }

    void assign(final TermFreqs other) {
        this.assign(other.term, other.rel, other.coll);
    }

    void divide(final int divisor) {
        this.assign(this.term / divisor, this.rel / divisor, this.coll / divisor);
    }

    void scale(final double factor) {
        this.assign(
            TermFreqs.round(this.term * factor), TermFreqs.round(this.rel * factor),
            TermFreqs.round(this.coll * factor)
        );
    }

    TermFreqs copy() {
        return new TermFreqs(this.term, this.rel, this.coll);
    }

    static long round(final double value) {
        return (long) (value + 0.5);
    }
}
