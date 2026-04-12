/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
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
@SuppressWarnings("PMD.TooManyMethods")
public interface Enquire {

    /**
     * Sets the query for the next search.
     *
     * <p>The weighting scheme is kept; BM25 with default parameters is used
     * until one is set.</p>
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
     * @param max Maximum number of items to return, zero for none
     * @return Match set
     */
    MSet getMSet(int first, int max);

    /**
     * Runs the search, counting at least a number of matching documents.
     *
     * @param first Zero-based index of the first item to return
     * @param max Maximum number of items to return, zero for none
     * @param atleast Minimum number of matching documents to count, which
     *  makes the bounds exact when no more documents match
     * @return Match set
     */
    MSet getMSet(int first, int max, int atleast);

    /**
     * Runs the search with a document filter and returns the match set.
     *
     * @param first Zero-based index of the first item to return
     * @param max Maximum number of items to return, zero for none
     * @param decider Optional filter applied to each candidate
     * @return Match set
     */
    MSet getMSet(int first, int max, MatchDecider decider);

    /**
     * Estimates the number of matching documents without producing the MSet.
     *
     * @return Estimated match count, zero when the query matches nothing
     */
    TermCount getMatchEstimate();

    /**
     * Sets a percentage cutoff with no minimum weight.
     *
     * @param percent Percentage of the best weight a match needs, zero for
     *  no cutoff
     */
    void setCutoff(int percent);

    /**
     * Sets a percentage cutoff and a minimum weight: matches weighing less
     * than the percentage of the best weight, or less than the minimum
     * weight, are left out.
     *
     * @param percent Percentage of the best weight, zero for no cutoff
     * @param weight Minimum weight, zero for none
     */
    void setCutoff(int percent, double weight);

    /**
     * Sets a time limit for the search.
     *
     * @param limit Maximum duration; null for no limit
     */
    void setTimeLimit(Duration limit);

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
    void setSortByValue(ValueNo slot, boolean ascending);

    /**
     * Sets sorting by a key extracted via a keymaker.
     *
     * @param maker Key maker to extract sort keys
     * @param ascending True for ascending order
     */
    void setSortByKey(KeyMaker maker, boolean ascending);

    /**
     * Sets a match spy to observe the matching process.
     *
     * @param spy Spy notified during matching; null to clear
     */
    void setMatchSpy(MatchSpy spy);

    /**
     * Sets a key maker for collapsing.
     *
     * @param maker Key maker for grouping results; null to clear
     */
    void setKeyMaker(KeyMaker maker);

    /**
     * Sets the maximum number of documents in a collapsed group.
     *
     * @param max Maximum group size; zero for no collapsing
     */
    void setCollapseMax(int max);

    /**
     * Sets the collapse key for a group.
     *
     * @param key Key to use for collapsing; empty to clear
     */
    void setCollapseKey(String key);

    /**
     * Generates an expansion set from the query.
     *
     * @param max Maximum number of terms to return
     * @return Expansion set
     */
    ESet getESet(int max);

    /**
     * Generates an expansion set with a custom expansion weight.
     *
     * @param max Maximum number of terms to return
     * @param weight Expansion weight scheme to use
     * @return Expansion set
     */
    ESet getESet(int max, ExpandWeight weight);

    /**
     * Generates an expansion set with a custom expansion weight and decider.
     *
     * @param max Maximum number of terms to return
     * @param weight Expansion weight scheme to use
     * @param chooser Filter for expansion terms
     * @return Expansion set
     */
    ESet getESet(int max, ExpandWeight weight, ExpandDecider chooser);

    /**
     * Clears all options, resetting to defaults.
     */
    void clear();
}
