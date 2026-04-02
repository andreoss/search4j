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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Posting list which replaces itself when advanced.
 *
 * @since 0.1.0
 */
final class BooleanPostListFixtureReplacing implements PostList {

    /**
     * Wrapped list.
     */
    private final PostList origin;

    /**
     * Counter told about each replacement.
     */
    private final AtomicInteger count;

    /**
     * Creates the list.
     *
     * @param origin Wrapped list
     * @param count Counter told about each replacement
     */
    BooleanPostListFixtureReplacing(
        final PostList origin,
        final AtomicInteger count
    ) {
        this.origin = origin;
        this.count = count;
    }

    @Override
    public DocId docId() {
        return this.origin.docId();
    }

    @Override
    public boolean atEnd() {
        return this.origin.atEnd();
    }

    @Override
    public PostList next(final double least) {
        return this.replace(this.origin.next(least));
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        return this.replace(this.origin.skipTo(target, least));
    }

    @Override
    public Probe check(final DocId target, final double least) {
        final Probe probe = this.origin.check(target, least);
        return new Probe(
            this.replace(probe.list()),
            probe.valid()
        );
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

    private PostList replace(final PostList moved) {
        return new BooleanPostListFixtureReplacing(moved, this.count);
    }
}
