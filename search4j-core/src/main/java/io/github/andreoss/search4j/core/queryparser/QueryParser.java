/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.QueryParserError;
import io.github.andreoss.search4j.core.termgen.Stemmer;
import io.github.andreoss.search4j.core.termgen.Stopper;
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.StemStrategy;
import java.util.EnumSet;
import java.util.Set;

/**
 * Parses query strings into queries with positions, stemming and stop words.
 *
 * @since 0.1.0
 */
public final class QueryParser {

    /**
     * Operators accepted as the default operator.
     */
    private static final Set<QueryOp> DEFAULTS = Set.of(
        QueryOp.AND, QueryOp.OR, QueryOp.NEAR, QueryOp.PHRASE,
        QueryOp.ELITE_SET, QueryOp.SYNONYM, QueryOp.MAX
    );

    /**
     * Operator joining words without an explicit operator.
     */
    private QueryOp defop;

    /**
     * Stemming algorithm.
     */
    private Stemmer stem;

    /**
     * Which words are stemmed.
     */
    private StemStrategy strategy;

    /**
     * Stop words.
     */
    private Stopper stop;

    /**
     * Creates a parser joining words by OR, without stemming or stop words.
     */
    public QueryParser() {
        this.defop = QueryOp.OR;
        this.stem = Stemmer.NONE;
        this.strategy = StemStrategy.SOME;
        this.stop = new Stopper();
    }

    /**
     * Sets the operator joining words without an explicit operator.
     *
     * @param oper AND, OR, NEAR, PHRASE, ELITE_SET, SYNONYM or MAX
     */
    public void defaultOp(final QueryOp oper) {
        if (!QueryParser.DEFAULTS.contains(oper)) {
            throw new InvalidArgumentError(
                "Default operator must be AND, OR, NEAR, PHRASE, ELITE_SET, SYNONYM or MAX"
            );
        }
        this.defop = oper;
    }

    /**
     * The operator joining words without an explicit operator.
     *
     * @return Default operator
     */
    public QueryOp defaultOp() {
        return this.defop;
    }

    /**
     * Sets the stemming algorithm and which words it applies to.
     *
     * @param reduce Stemming algorithm
     * @param mode Which words are stemmed
     */
    public void stemmer(final Stemmer reduce, final StemStrategy mode) {
        this.stem = reduce;
        this.strategy = mode;
    }

    /**
     * Sets the stop words.
     *
     * @param words Stop words
     */
    public void stopper(final Stopper words) {
        this.stop = words;
    }

    /**
     * Parses a query with the default flags.
     *
     * @param query Query text
     * @return Parsed query
     */
    public Query parse(final String query) {
        return this.parse(query, ParseFlag.DEFAULTS);
    }

    /**
     * Parses a query, parsing it again without syntax features on a syntax
     * error.
     *
     * @param query Query text
     * @param flags Syntax features enabled
     * @return Parsed query, match-nothing when no word remains
     */
    public Query parse(final String query, final Set<ParseFlag> flags) {
        final Setup setup = new Setup(
            Set.copyOf(flags), this.defop, this.stem, this.strategy, this.stop
        );
        Query result;
        try {
            result = QueryParser.once(query, setup);
        } catch (final QueryParserError err) {
            if (!Grammar.FAILURE.equals(err.getMessage())) {
                throw err;
            }
            final Set<ParseFlag> kept = EnumSet.noneOf(ParseFlag.class);
            kept.addAll(flags);
            kept.retainAll(ParseFlag.RETAINED);
            result = QueryParser.once(query, setup.with(kept));
        }
        return result;
    }

    private static Query once(final String query, final Setup setup) {
        return new Grammar(new Lexer(query, setup).lex(), setup).parse();
    }
}
