/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term generation from text: words, prefixes, positions and n-grams.
 */
package io.github.andreoss.search4j.core.termgen;

/**
 * Reduces a word to its stem.
 *
 * <p>The term generator accepts any implementation; the language stemmers
 * arrive with the stemming iterations.</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface Stemmer {

    /**
     * Stemmer returning words unchanged.
     */
    Stemmer NONE = word -> word;

    /**
     * Stem of a word.
     *
     * @param word Word in lower case
     * @return Stem, empty when the word has none
     */
    String stem(String word);

    /**
     * Name of the stemming algorithm.
     *
     * @return Algorithm name, empty for the identity stemmer
     */
    default String name() {
        return "";
    }
}
