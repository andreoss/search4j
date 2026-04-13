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
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Synthetic posting list over fixed documents.
 *
 * @since 0.1.0
 */
final class BooleanPostListFixtureFake implements PostList {

    /**
     * Description text.
     */
    private final String name;

    /**
     * Documents listed.
     */
    private final List<DocId> docs;

    /**
     * Index of the current document, minus one before the start.
     */
    private int index;

    /**
     * Estimated number of documents.
     */
    private final TermCount frequency;

    /**
     * Weight of every document.
     */
    private double weight;

    /**
     * Highest weight the list can return.
     */
    private double maximum;

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
    private DocRange range;

    /**
     * Positions assigned to the current document.
     */
    private List<TermPos> positions;

    /**
     * Least weight hint received by the last move.
     */
    private double least;

    /**
     * Creates the list.
     *
     * @param name Description text
     * @param docs Documents listed
     * @param freq Estimated number of documents
     * @param span Range of possible documents
     * @param positions Positions of the current document
     */
    BooleanPostListFixtureFake(
        final String name,
        final List<DocId> docs,
        final TermCount freq,
        final DocRange span,
        final List<TermPos> positions
    ) {
        this.name = name;
        this.docs = docs;
        this.index = -1;
        this.frequency = freq;
        this.weight = 0.0;
        this.maximum = 0.0;
        this.wdf = new TermCount(1);
        this.subqueries = new TermCount(1);
        this.range = span;
        this.positions = positions;
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
    public PostList next(final double hint) {
        this.least = hint;
        this.index += 1;
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double hint) {
        this.least = hint;
        while (this.index < 0
            || this.index < this.docs.size()
            && this.docId().compareTo(target) < 0) {
            this.index += 1;
        }
        return this;
    }

    @Override
    public Probe check(final DocId target, final double hint) {
        final Probe result;
        if (!this.atEnd() && target.compareTo(this.docId()) <= 0) {
            result = new Probe(this, this.docId().equals(target));
        } else {
            final PostList moved = this.skipTo(target, hint);
            result = new Probe(
                moved,
                !moved.atEnd() && moved.docId().equals(target)
            );
        }
        return result;
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
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        sink.accept(new VectorPositionList(this.positions));
    }

    @Override
    public PositionList readPositionList() {
        return new VectorPositionList(this.positions);
    }

    @Override
    public String description() {
        return this.name;
    }

    /**
     * Least weight received by the last move.
     *
     * @return Least weight hint
     */
    double lastLeast() {
        return this.least;
    }

    BooleanPostListFixtureFake scoring(final double value) {
        this.weight = value;
        return this;
    }

    BooleanPostListFixtureFake maxed(final double value) {
        this.maximum = value;
        return this;
    }

    BooleanPostListFixtureFake freq(final long value) {
        this.wdf = new TermCount(value);
        return this;
    }

    BooleanPostListFixtureFake wdf(final long value) {
        return this.freq(value);
    }

    BooleanPostListFixtureFake subs(final int value) {
        this.subqueries = new TermCount(value);
        return this;
    }

    BooleanPostListFixtureFake span(final int first, final int last) {
        this.range = new DocRange(DocId.of(first), DocId.of(last));
        return this;
    }

    BooleanPostListFixtureFake positions(final int... values) {
        final List<TermPos> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(new TermPos(value));
        }
        this.positions = result;
        return this;
    }
}
