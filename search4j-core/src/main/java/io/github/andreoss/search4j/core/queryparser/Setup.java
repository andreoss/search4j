/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.termgen.Stemmer;
import io.github.andreoss.search4j.core.termgen.Stopper;
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.StemStrategy;
import java.util.Set;

/**
 * Settings of one parse: flags, default operator, stemming and stop words.
 *
 * @param flags Syntax features enabled
 * @param defop Operator joining words without an explicit operator
 * @param stemmer Stemming algorithm
 * @param strategy Which words are stemmed
 * @param stopper Stop words
 * @since 0.1.0
 */
record Setup(
    Set<ParseFlag> flags, QueryOp defop, Stemmer stemmer,
    StemStrategy strategy, Stopper stopper
) {

    /**
     * Tests whether a flag is enabled.
     *
     * @param flag Flag to test
     * @return True when enabled
     */
    boolean has(final ParseFlag flag) {
        return this.flags.contains(flag);
    }

    /**
     * Tests whether a word is a stop word.
     *
     * @param word Word to test
     * @return True when stopped
     */
    boolean stopped(final Word word) {
        return this.stopper.words().contains(word.name());
    }

    /**
     * Tests whether no stemming algorithm is set.
     *
     * @return True for the identity stemmer
     */
    boolean unstemmed() {
        return this.stemmer.equals(Stemmer.NONE)
            || "none".equals(this.stemmer.name());
    }

    /**
     * The default operator in effect.
     *
     * @return Default operator, a conjunction when positions are disabled
     */
    QueryOp operator() {
        final QueryOp result;
        if (this.has(ParseFlag.NO_POSITIONS) && Setup.positional(this.defop)) {
            result = QueryOp.AND;
        } else {
            result = this.defop;
        }
        return result;
    }

    /**
     * Tests whether an operator depends on positions.
     *
     * @param oper Operator
     * @return True for NEAR and PHRASE
     */
    static boolean positional(final QueryOp oper) {
        return oper == QueryOp.NEAR || oper == QueryOp.PHRASE;
    }

    /**
     * The same settings with other flags.
     *
     * @param kept Flags of the new settings
     * @return Settings with the given flags
     */
    Setup with(final Set<ParseFlag> kept) {
        return new Setup(
            kept, this.defop, this.stemmer, this.strategy, this.stopper
        );
    }
}
