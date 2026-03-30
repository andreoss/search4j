/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.EmptyTermList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Spelling and synonym dictionaries of a shard.
 *
 * <p>Readers find nothing by default; writers raise an unimplemented
 * error.</p>
 *
 * @since 0.1.0
 */
public interface ShardDictionaries {

    /**
     * Opens the spelling fragments of a word.
     *
     * @param word Word to look up
     * @return Fragments, none by default
     */
    default TermList openSpellingTermList(final String word) {
        return EmptyTermList.INSTANCE;
    }

    /**
     * Opens every spelling word.
     *
     * @return Words with frequencies, none by default
     */
    default TermList openSpellingWordList() {
        return EmptyTermList.INSTANCE;
    }

    /**
     * Frequency of a spelling word.
     *
     * @param word Word to look up
     * @return Frequency, zero by default
     */
    default TermCount spellingFrequency(final String word) {
        return TermCount.ZERO;
    }

    /**
     * Adds to the frequency of a spelling word.
     *
     * @param word Word to add
     * @param increment Amount added
     */
    default void addSpelling(final String word, final TermCount increment) {
        throw new UnimplementedError(
            "This backend does not implement spelling correction"
        );
    }

    /**
     * Takes from the frequency of a spelling word.
     *
     * @param word Word to reduce
     * @param decrement Amount taken
     * @return Amount of the decrement left unapplied
     */
    default TermCount removeSpelling(
        final String word,
        final TermCount decrement
    ) {
        throw new UnimplementedError(
            "This backend does not implement spelling correction"
        );
    }

    /**
     * Opens the synonyms of a term.
     *
     * @param term Term to look up
     * @return Synonyms, none by default
     */
    default TermList openSynonyms(final String term) {
        return EmptyTermList.INSTANCE;
    }

    /**
     * Opens the terms with synonyms starting with a prefix.
     *
     * @param prefix Leading text, empty for all
     * @return Terms, none by default
     */
    default TermList openSynonymKeys(final String prefix) {
        return EmptyTermList.INSTANCE;
    }

    /**
     * Adds a synonym of a term.
     *
     * @param term Term to extend
     * @param synonym Synonym to add
     */
    default void addSynonym(final String term, final String synonym) {
        throw new UnimplementedError(
            "This backend does not implement synonyms"
        );
    }

    /**
     * Removes a synonym of a term.
     *
     * @param term Term to reduce
     * @param synonym Synonym to remove
     */
    default void removeSynonym(final String term, final String synonym) {
        throw new UnimplementedError(
            "This backend does not implement synonyms"
        );
    }

    /**
     * Removes every synonym of a term.
     *
     * @param term Term to clear
     */
    default void clearSynonyms(final String term) {
        throw new UnimplementedError(
            "This backend does not implement synonyms"
        );
    }
}
