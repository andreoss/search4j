/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Shared fixtures for the boolean posting list tests.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Posting list which reports its first document before it is started and
 * whose checks always move to the first document at or after the target.
 *
 * @since 0.5.2
 */
final class BooleanPostListFixtureEager implements PostList {

    /**
     * Wrapped list.
     */
    private final PostList origin;

    /**
     * First document of the wrapped list.
     */
    private final DocId first;

    /**
     * Creates the list.
     *
     * @param origin Wrapped list, not yet started
     * @param first First document of the wrapped list
     */
    BooleanPostListFixtureEager(final PostList origin, final DocId first) {
        this.origin = origin;
        this.first = first;
    }

    @Override
    public DocId docId() {
        DocId result = this.origin.docId();
        if (result.equals(DocId.NONE) && !this.origin.atEnd()) {
            result = this.first;
        }
        return result;
    }

    @Override
    public boolean atEnd() {
        return this.origin.atEnd();
    }

    @Override
    public PostList next(final double least) {
        return this.origin.next(least);
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        return this.origin.skipTo(target, least);
    }

    @Override
    public Probe check(final DocId target, final double least) {
        return new Probe(this.origin.skipTo(target, least), true);
    }

    @Override
    public TermCount termFrequency() {
        return this.origin.termFrequency();
    }

    @Override
    public double weight(final DocStats doc) {
        return this.origin.weight(doc);
    }

    @Override
    public double recalcMaxWeight() {
        return this.origin.recalcMaxWeight();
    }

    @Override
    public TermCount wdf() {
        return this.origin.wdf();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.origin.countMatchingSubqueries();
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return this.origin.docIdRange(whole);
    }

    @Override
    public String description() {
        return this.origin.description();
    }
}
