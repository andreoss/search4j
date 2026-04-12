/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.error.QueryParserError;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import java.util.Optional;

/**
 * Words and phrases joined by the default operator, with required and
 * excluded parts.
 *
 * @since 0.1.0
 */
final class Prob {

    /**
     * No part.
     */
    private static final Optional<Query> NONE = Optional.empty();

    /**
     * Parse settings.
     */
    private final Setup setup;

    /**
     * Optional parts.
     */
    private Optional<Query> query;

    /**
     * Required parts.
     */
    private Optional<Query> love;

    /**
     * Excluded parts.
     */
    private Optional<Query> hate;

    /**
     * Creates an empty group.
     *
     * @param setup Parse settings
     */
    Prob(final Setup setup) {
        this.setup = setup;
        this.query = Prob.NONE;
        this.love = Prob.NONE;
        this.hate = Prob.NONE;
    }

    /**
     * Starts the group with two optional parts.
     *
     * @param first First part, absent when stopped
     * @param second Second part, absent when stopped
     */
    void pair(final Optional<Query> first, final Optional<Query> second) {
        this.query = first;
        if (second.isPresent()) {
            final QueryOp oper = this.setup.operator();
            if (first.isPresent() && Setup.positional(oper)) {
                this.query = Optional.of(
                    Query.of(oper, List.of(first.get(), second.get()), 11)
                );
            } else {
                this.query = Optional.of(
                    Joins.add(this.query, oper, second.get())
                );
            }
        }
    }

    /**
     * Starts the group with one optional part.
     *
     * @param first Part, absent when stopped
     */
    void single(final Optional<Query> first) {
        this.query = first;
    }

    /**
     * Adds an optional part.
     *
     * @param part Part, absent when stopped
     */
    void add(final Optional<Query> part) {
        if (part.isPresent()) {
            this.query = Optional.of(
                Joins.add(this.query, this.setup.operator(), part.get())
            );
        }
    }

    /**
     * Adds a required part.
     *
     * @param part Part
     */
    void require(final Optional<Query> part) {
        if (part.isPresent()) {
            if (this.setup.operator() == QueryOp.AND) {
                this.query = Optional.of(
                    Joins.add(this.query, QueryOp.AND, part.get())
                );
            } else {
                this.love = Optional.of(
                    Joins.add(this.love, QueryOp.AND, part.get())
                );
            }
        }
    }

    /**
     * Adds an excluded part.
     *
     * @param part Part
     */
    void exclude(final Optional<Query> part) {
        if (part.isPresent()) {
            this.hate = Optional.of(
                Joins.add(this.hate, QueryOp.OR, part.get())
            );
        }
    }

    /**
     * Builds the query of the group.
     *
     * @return Query, absent when every part was stopped
     */
    Optional<Query> build() {
        Optional<Query> result = this.query;
        if (this.love.isPresent()) {
            final Query must = this.love.get();
            if (must.empty() || result.isEmpty()) {
                result = this.love;
            } else {
                result = Optional.of(
                    Query.of(QueryOp.AND_MAYBE, must, result.get())
                );
            }
        }
        if (this.hate.isPresent() && !this.hate.get().empty()) {
            if (result.isEmpty()) {
                throw new QueryParserError(Grammar.FAILURE);
            }
            result = Optional.of(
                Query.of(QueryOp.AND_NOT, result.get(), this.hate.get())
            );
        }
        return result;
    }
}
