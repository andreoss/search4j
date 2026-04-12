/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * A word read from a query with its position and stemming decision.
 *
 * @param name Lower-cased word
 * @param stem Stemming applied to the word
 * @param pos Position of the word in the query
 * @since 0.1.0
 */
record Word(String name, StemStrategy stem, TermPos pos) {

    /**
     * The same word used where positions matter.
     *
     * @return The word, unstemmed when only some words are stemmed
     */
    Word positional() {
        final Word result;
        if (this.stem == StemStrategy.SOME) {
            result = new Word(this.name, StemStrategy.NONE, this.pos);
        } else {
            result = this;
        }
        return result;
    }

    /**
     * The index term of the word.
     *
     * @param setup Parser settings holding the stemmer
     * @return Term text, marked when stemmed selectively
     */
    String term(final Setup setup) {
        final StringBuilder text = new StringBuilder(this.name.length() + 1);
        if (this.stem != StemStrategy.NONE && this.stem != StemStrategy.ALL) {
            text.append('Z');
        }
        if (this.stem == StemStrategy.NONE) {
            text.append(this.name);
        } else {
            text.append(setup.stemmer().stem(this.name));
        }
        return text.toString();
    }

    /**
     * The term query of the word at its position.
     *
     * @param setup Parser settings holding the stemmer
     * @return Term query
     */
    Query query(final Setup setup) {
        return Query.term(this.term(setup), new TermCount(1), this.pos);
    }
}
