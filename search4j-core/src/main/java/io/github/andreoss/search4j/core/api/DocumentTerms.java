/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Terms of a document, with within-document frequencies and positions.
 *
 * @since 0.1.0
 */
public interface DocumentTerms {

    /**
     * Adds a term without a position, increasing its within-document
     * frequency.
     *
     * @param term Non-empty term
     * @param increment Amount added to the frequency, zero for a boolean term
     */
    void addTerm(String term, TermCount increment);

    /**
     * Adds a term at a position, increasing its within-document frequency.
     *
     * <p>A position already present is kept once; the frequency still
     * grows.</p>
     *
     * @param term Non-empty term
     * @param position Position to record
     * @param increment Amount added to the frequency
     */
    void addPosting(String term, TermPos position, TermCount increment);

    /**
     * Removes a term with all its positions.
     *
     * <p>An absent term raises an invalid-argument error.</p>
     *
     * @param term Non-empty term present in the document
     */
    void removeTerm(String term);

    /**
     * Removes one position of a term and decreases its frequency, never below
     * zero; the term goes once it has neither frequency nor positions.
     *
     * <p>An absent term or position raises an invalid-argument error.</p>
     *
     * @param term Non-empty term present in the document
     * @param position Position present for the term
     * @param decrement Amount taken from the frequency
     */
    void removePosting(String term, TermPos position, TermCount decrement);

    /**
     * Removes every term.
     */
    void clearTerms();

    /**
     * Number of distinct terms in the document.
     *
     * @return Distinct term count, zero when empty
     */
    TermCount termCount();

    /**
     * Opens the terms in ascending order with frequencies and positions.
     *
     * @return Iterator over a snapshot of the terms
     */
    TermIterator termList();
}
