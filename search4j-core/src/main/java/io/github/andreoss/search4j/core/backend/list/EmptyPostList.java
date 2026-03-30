/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Posting list holding no document.
 *
 * @since 0.1.0
 */
public enum EmptyPostList implements PostList {

    /**
     * The single empty list.
     */
    INSTANCE;

    @Override
    public DocId docId() {
        throw new InvalidOperationError("An empty posting list has no document");
    }

    @Override
    public boolean atEnd() {
        return true;
    }

    @Override
    public PostList next(final double least) {
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        return this;
    }

    @Override
    public String description() {
        return "EmptyPostList";
    }

    @Override
    public TermCount termFrequency() {
        return TermCount.ZERO;
    }

    @Override
    public double weight(final DocStats doc) {
        return 0.0;
    }

    @Override
    public double recalcMaxWeight() {
        return 0.0;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return TermCount.ZERO;
    }
}
