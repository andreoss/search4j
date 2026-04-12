/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * A token read from a query: an operator, a bracket, a quote or a word.
 *
 * @param kind Token kind
 * @param word Word carried, blank for other tokens
 * @param width Window of a positional operator as an unsigned count, zero
 *  for the default
 * @since 0.1.0
 */
record Lexeme(Lexeme.Kind kind, Word word, int width) {

    /**
     * Word carried by tokens that carry none.
     */
    static final Word BLANK = new Word("", StemStrategy.NONE, TermPos.FIRST);

    /**
     * A token without a word.
     *
     * @param kind Token kind
     * @return Token
     */
    static Lexeme of(final Lexeme.Kind kind) {
        return new Lexeme(kind, Lexeme.BLANK, 0);
    }

    /**
     * A token carrying a word.
     *
     * @param kind Token kind
     * @param word Word
     * @return Token
     */
    static Lexeme of(final Lexeme.Kind kind, final Word word) {
        return new Lexeme(kind, word, 0);
    }

    /**
     * Token kinds.
     *
     * @since 0.1.0
     */
    enum Kind {

        /**
         * A word on its own.
         */
        TERM,

        /**
         * A word following another word after white space.
         */
        GROUP_TERM,

        /**
         * A word following another word after a phrase character.
         */
        PHR_TERM,

        /**
         * A word ending in a wildcard.
         */
        WILD_TERM,

        /**
         * The final word, matched as a prefix too.
         */
        PARTIAL_TERM,

        /**
         * A group of words may be empty after stopping.
         */
        EMPTY_GROUP_OK,

        /**
         * A double quote.
         */
        QUOTE,

        /**
         * An opening bracket.
         */
        BRA,

        /**
         * A closing bracket.
         */
        KET,

        /**
         * Conjunction.
         */
        AND,

        /**
         * Disjunction.
         */
        OR,

        /**
         * Difference.
         */
        NOT,

        /**
         * Exclusive disjunction.
         */
        XOR,

        /**
         * Synonym of two expressions.
         */
        SYN,

        /**
         * Unordered proximity.
         */
        NEAR,

        /**
         * Ordered proximity.
         */
        ADJ,

        /**
         * Required word.
         */
        LOVE,

        /**
         * Excluded word.
         */
        HATE,

        /**
         * Excluded word after a conjunction.
         */
        HATE_AFTER_AND,

        /**
         * Synonym expansion of the next word.
         */
        SYNONYM,

        /**
         * End of the query.
         */
        END
    }
}
