/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * A document considered for a match set: its identifier, its weight once
 * calculated, and its sort key when sorting by value.
 *
 * @since 0.4.0
 */
final class Candidate {

    /**
     * Document identifier.
     */
    private final DocId docid;

    /**
     * Weight of the document, zero until calculated.
     */
    private double score;

    /**
     * Sort key of the document, empty when not sorting by value.
     */
    private String sortkey;

    /**
     * Creates a candidate.
     *
     * @param docid Document identifier
     * @param score Weight of the document, zero when not calculated
     */
    Candidate(final DocId docid, final double score) {
        this.docid = docid;
        this.score = score;
        this.sortkey = "";
    }

    DocId docId() {
        return this.docid;
    }

    double weight() {
        return this.score;
    }

    void weight(final double value) {
        this.score = value;
    }

    String key() {
        return this.sortkey;
    }

    void key(final String value) {
        this.sortkey = value;
    }
}
