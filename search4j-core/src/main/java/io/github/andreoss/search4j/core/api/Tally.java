/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Running counters and thresholds of one collection of matches.
 *
 * @since 0.4.0
 */
final class Tally {

    /**
     * Minimum weight a match needs.
     */
    private double minimum;

    /**
     * Highest weight seen.
     */
    private double maximum;

    /**
     * Whether kept matches still need checking against the minimum weight.
     */
    private boolean stale;

    /**
     * Number of matching documents counted.
     */
    private int counted;

    /**
     * Number of matching documents to count before stopping.
     */
    private int check;

    /**
     * Matching subqueries of the document with the highest weight.
     */
    private int subqueries;

    /**
     * Number of documents a filter accepted.
     */
    private int accepted;

    /**
     * Number of documents a filter rejected.
     */
    private int rejected;

    /**
     * Creates a tally with nothing counted.
     *
     * @param check Number of matching documents to count before stopping
     */
    Tally(final int check) {
        this.check = check;
    }

    double least() {
        return this.minimum;
    }

    void least(final double value) {
        this.minimum = value;
    }

    double best() {
        return this.maximum;
    }

    void best(final double value) {
        this.maximum = value;
    }

    boolean pending() {
        return this.stale;
    }

    void pending(final boolean value) {
        this.stale = value;
    }

    int known() {
        return this.counted;
    }

    void count() {
        this.counted = this.counted + 1;
    }

    int atleast() {
        return this.check;
    }

    void atleast(final int value) {
        this.check = value;
    }

    void decide(final boolean accept) {
        if (accept) {
            this.accepted = this.accepted + 1;
        } else {
            this.rejected = this.rejected + 1;
        }
    }

    int accepted() {
        return this.accepted;
    }

    int rejected() {
        return this.rejected;
    }

    int matched() {
        return this.subqueries;
    }

    void matched(final int value) {
        this.subqueries = value;
    }
}
