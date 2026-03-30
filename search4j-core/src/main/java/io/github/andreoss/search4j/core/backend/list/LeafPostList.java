/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Posting list of a single term, read straight from a backend.
 *
 * <p>It is unweighted until a weight is attached, and then scores each
 * posting from its within-document frequency.</p>
 *
 * @since 0.1.0
 */
public abstract class LeafPostList implements PostList {

    /**
     * The term listed, empty for every document.
     */
    private String name;

    /**
     * The attached weight, empty while unweighted.
     */
    private Optional<TermWeight> scheme;

    /**
     * Creates the list of a term.
     *
     * @param term Term listed, empty for every document
     */
    protected LeafPostList(final String term) {
        this(Objects.requireNonNull(term, "term"), Optional.empty());
    }

    /**
     * Creates the list of a term with a weight.
     *
     * @param term Term listed, empty for every document
     * @param scheme Attached weight, empty while unweighted
     */
    private LeafPostList(final String term, final Optional<TermWeight> scheme) {
        this.name = term;
        this.scheme = scheme;
    }

    /**
     * Sum of the within-document frequencies of the term.
     *
     * @return Collection frequency
     */
    public abstract TermCount collectionFrequency();

    /**
     * Upper bound on the within-document frequency of the term.
     *
     * @return Highest frequency any posting can have
     */
    public abstract TermCount wdfUpperBound();

    /**
     * Opens a list for another term sharing this list's source, when that is
     * cheaper than opening it afresh.
     *
     * @param term Term to list
     * @param positions True when positions will be read
     * @return List for the term, empty when not opened this way
     */
    public abstract Optional<LeafPostList> openNearby(
        String term, boolean positions
    );

    /**
     * Attaches the weight scoring the postings; it can be attached once.
     *
     * @param weight Weight of the term
     */
    public final void weighting(final TermWeight weight) {
        if (this.scheme.isPresent()) {
            throw new InvariantError("Weight is already attached");
        }
        this.scheme = Optional.of(weight);
    }

    /**
     * Term listed.
     *
     * @return Term, empty for every document
     */
    public final String term() {
        return this.name;
    }

    /**
     * Changes the term the list reports, keeping its postings.
     *
     * @param term New term
     */
    public final void rename(final String term) {
        this.name = Objects.requireNonNull(term, "term");
    }

    @Override
    public final double weight(final DocStats doc) {
        return this.scheme.map(
            weight -> weight.sumPart(this.wdf(), doc)
        ).orElse(0.0);
    }

    @Override
    public final double recalcMaxWeight() {
        return this.scheme.map(TermWeight::maxPart).orElse(0.0);
    }

    @Override
    public final TermCount countMatchingSubqueries() {
        TermCount result = TermCount.ZERO;
        if (this.scheme.isPresent()) {
            result = new TermCount(1);
        }
        return result;
    }

    @Override
    public final void gatherPositionLists(final Consumer<PositionList> sink) {
        sink.accept(this.readPositionList());
    }
}
