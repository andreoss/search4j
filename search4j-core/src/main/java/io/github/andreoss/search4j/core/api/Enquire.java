/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.time.Duration;

/**
 * The search orchestrator: builds and runs queries to produce match sets.
 *
 * <p>An Enquire instance is tied to a database and can be reused for
 * multiple searches. Weight schemes and options are reset for each
 * search by calling {@link #setQuery(Query)} or
 * {@link #setQuery(Query, Weight)}.</p>
 *
 * @since 0.1.0
 */
public interface Enquire {

    /**
     * Sets the query for the next search.
     *
     * <p>The default Boolean weight scheme is used; call
     * {@link #setQuery(Query, Weight)} to specify a weight scheme.</p>
     *
     * @param query Query to run
     */
    void setQuery(Query query);

    /**
     * Sets the query and weight scheme for the next search.
     *
     * @param query Query to run
     * @param weight Weight scheme to apply
     */
    void setQuery(Query query, Weight weight);

    /**
     * Sets the weighting scheme for the next search.
     *
     * @param weight Weight scheme to apply
     */
    void setWeightingScheme(Weight weight);

    /**
     * Runs the search and returns the match set.
     *
     * @param first Zero-based index of the first item to return
     * @param maxItems Maximum number of items to return, zero for all
     * @return Match set
     */
    MSet getMSet(int first, int maxItems);

    /**
     * Runs the search with a document filter and returns the match set.
     *
     * @param first Zero-based index of the first item to return
     * @param maxItems Maximum number of items to return, zero for all
     * @param matchDecider Optional filter applied to each candidate
     * @return Match set
     */
    MSet getMSet(int first, int maxItems, MatchDecider matchDecider);

    /**
     * Runs the search with a document filter and a check limit.
     *
     * @param first Zero-based index of the first item to return
     * @param maxItems Maximum number of items to return, zero for all
     * @param matchDecider Optional filter applied to each candidate
     * @param checkAtLeast Minimum number of documents to check before
     *  stopping early; zero for no early stop
     * @return Match set
     */
    MSet getMSet(
        int first, int maxItems, MatchDecider matchDecider, int checkAtLeast
    );

    /**
     * Estimates the number of matching documents without producing the MSet.
     *
     * @return Estimated match count, zero when the query matches nothing
     */
    TermCount getMatchEstimate();

    /**
     * Sets a percentage cutoff for early termination.
     *
     * @param percentCutoff Percentage of results above which to stop;
     *  zero for no cutoff
     */
    void setCutoff(int percentCutoff);

    /**
     * Sets a time limit for the search.
     *
     * @param timeLimit Maximum duration; null for no limit
     */
    void setTimeLimit(Duration timeLimit);

    /**
     * Sets the document ordering of the match set.
     *
     * @param order True for ascending document identifier order, false for
     *  descending weight order
     */
    void setDocIdOrder(boolean order);

    /**
     * Sets sorting by a value slot.
     *
     * @param slot Slot to sort by
     * @param ascending True for ascending order
     */
    void setSortByValue(io.github.andreoss.search4j.core.types.ValueNo slot, boolean ascending);

    /**
     * Sets sorting by a key extracted via a keymaker.
     *
     * @param keyMaker Key maker to extract sort keys
     * @param ascending True for ascending order
     */
    void setSortByKey(KeyMaker keyMaker, boolean ascending);

    /**
     * Sets a match spy to observe the matching process.
     *
     * @param matchSpy Spy notified during matching; null to clear
     */
    void setMatchSpy(MatchSpy matchSpy);

    /**
     * Sets a key maker for collapsing.
     *
     * @param keyMaker Key maker for grouping results; null to clear
     */
    void setKeyMaker(KeyMaker keyMaker);

    /**
     * Sets the maximum number of documents in a collapsed group.
     *
     * @param maxMax Maximum group size; zero for no collapsing
     */
    void setCollapseMax(int maxMax);

    /**
     * Sets the collapse key for a group.
     *
     * @param collapseKey Key to use for collapsing; empty to clear
     */
    void setCollapseKey(String collapseKey);

    /**
     * Generates an expansion set from the query.
     *
     * @param maxTerms Maximum number of terms to return
     * @return Expansion set
     */
    ESet getESet(int maxTerms);

    /**
     * Generates an expansion set with a custom expansion weight.
     *
     * @param maxTerms Maximum number of terms to return
     * @param weight Expansion weight scheme to use
     * @return Expansion set
     */
    ESet getESet(int maxTerms, ExpandWeight weight);

    /**
     * Generates an expansion set with a custom expansion weight and decider.
     *
     * @param maxTerms Maximum number of terms to return
     * @param weight Expansion weight scheme to use
     * @param decider Filter for expansion terms
     * @return Expansion set
     */
    ESet getESet(int maxTerms, ExpandWeight weight, ExpandDecider decider);

    /**
     * Clears all options, resetting to defaults.
     */
    void clear();
}
