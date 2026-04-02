/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list standing in for one produced by a backend, used by the
 * compatibility checks of the boolean operators.
 */
package io.github.andreoss.search4j.core.compat;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;

/**
 * Documents of one operator member, with the scores the operators combine.
 *
 * @since 0.1.0
 */
final class OraclePostList implements PostList {

    /**
     * Description text.
     */
    private final String name;

    /**
     * Documents in ascending order.
     */
    private final List<DocId> docs;

    /**
     * Estimated number of matching documents.
     */
    private TermCount frequency;

    /**
     * Within-document frequency of every document.
     */
    private TermCount wdf;

    /**
     * Number of matching subqueries.
     */
    private TermCount subqueries;

    /**
     * Range of possible documents.
     */
    private final DocRange range;

    /**
     * Weight of every document.
     */
    private final double weight;

    /**
     * Highest weight this member can return.
     */
    private final double maximum;

    /**
     * Index of the current document, minus one before the start.
     */
    private int index;

    /**
     * Creates a member.
     *
     * @param name Description text
     * @param docs Documents listed
     */
    OraclePostList(final String name, final int... docs) {
        this(
            name,
            OraclePostList.ids(docs),
            new TermCount(docs.length),
            new DocRange(DocId.of(1), DocId.of(9))
        );
    }

    private OraclePostList(
        final String name, final List<DocId> docs,
        final TermCount frequency, final DocRange range
    ) {
        this.name = name;
        this.docs = docs;
        this.frequency = frequency;
        this.wdf = new TermCount(1);
        this.subqueries = new TermCount(1);
        this.range = range;
        this.weight = 0.0;
        this.maximum = 0.0;
        this.index = -1;
    }

    @Override
    public DocId docId() {
        final DocId result;
        if (this.index < 0 || this.index >= this.docs.size()) {
            result = DocId.NONE;
        } else {
            result = this.docs.get(this.index);
        }
        return result;
    }

    @Override
    public boolean atEnd() {
        return this.index >= this.docs.size();
    }

    @Override
    public PostList next(final double least) {
        this.index += 1;
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        while (this.index < 0
            || this.index < this.docs.size()
            && this.docId().compareTo(target) < 0) {
            this.index += 1;
        }
        return this;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public double weight(final DocStats doc) {
        return this.weight;
    }

    @Override
    public double recalcMaxWeight() {
        return this.maximum;
    }

    @Override
    public TermCount wdf() {
        return this.wdf;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.subqueries;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return this.range;
    }

    @Override
    public String description() {
        return this.name;
    }

    OraclePostList freq(final int value) {
        this.wdf = new TermCount(value);
        return this;
    }

    OraclePostList subs(final int value) {
        this.subqueries = new TermCount(value);
        return this;
    }

    OraclePostList estimate(final int value) {
        this.frequency = new TermCount(value);
        return this;
    }

    private static List<DocId> ids(final int... values) {
        final List<DocId> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(DocId.of(value));
        }
        return result;
    }
}
