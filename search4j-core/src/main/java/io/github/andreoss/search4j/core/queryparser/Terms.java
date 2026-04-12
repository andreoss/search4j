/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Expansion;
import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the queries of compound terms: phrases, proximity runs, word
 * groups, wildcards and partial words.
 *
 * @since 0.1.0
 */
final class Terms {

    /**
     * Expansion of a partial final word.
     */
    private static final Expansion PARTIAL = new Expansion(
        new TermCount(100), WildcardLimit.MOST_FREQUENT
    );

    /**
     * Parse settings.
     */
    private final Setup setup;

    /**
     * Creates a builder.
     *
     * @param setup Parse settings
     */
    Terms(final Setup setup) {
        this.setup = setup;
    }

    /**
     * Words in order, as a phrase.
     *
     * @param words Words of the phrase
     * @return Phrase query
     */
    Optional<Query> phrase(final List<Word> words) {
        return this.window(QueryOp.PHRASE, words, 0);
    }

    /**
     * Words joined by NEAR or ADJ.
     *
     * @param words Words of the run
     * @param near True for NEAR, false for ADJ
     * @param width Widest explicit window as an unsigned count, zero when none
     * @return Proximity query
     */
    Optional<Query> proximity(
        final List<Word> words, final boolean near, final int width
    ) {
        int span = width;
        if (span == 0) {
            span = 10;
        }
        final QueryOp oper;
        if (near) {
            oper = QueryOp.NEAR;
        } else {
            oper = QueryOp.PHRASE;
        }
        return this.window(oper, words, span - 1);
    }

    /**
     * Words separated by white space, joined by the default operator.
     *
     * @param group Words of the group
     * @return Group query, absent when every word was stopped
     */
    Optional<Query> group(final Terms.Group group) {
        final QueryOp oper = this.setup.operator();
        final boolean positional = Setup.positional(oper);
        List<Query> parts = this.members(group.words(), positional, true);
        if (parts.isEmpty() && !group.empty()
            && group.words().stream().anyMatch(this.setup::stopped)) {
            parts = this.members(group.words(), positional, false);
        }
        final Optional<Query> result;
        if (parts.isEmpty()) {
            result = Optional.empty();
        } else if (positional) {
            result = Optional.of(
                Query.of(oper, parts, parts.size() + 9)
            );
        } else {
            result = Optional.of(Query.of(oper, parts));
        }
        return result;
    }

    /**
     * A word ending in a wildcard.
     *
     * @param word Word before the wildcard
     * @return Wildcard query
     */
    static Optional<Query> wildcard(final Word word) {
        return Optional.of(
            Query.of(
                QueryOp.SYNONYM,
                List.of(
                    Query.wildcard(
                        Wildcard.of(word.name()).withCombiner(QueryOp.OR)
                    )
                )
            )
        );
    }

    /**
     * The final word, matched as a prefix or as itself.
     *
     * @param word Final word
     * @return Partial word query
     */
    Optional<Query> partial(final Word word) {
        final List<Query> full = new ArrayList<>(1);
        if (!this.setup.stopped(word)) {
            full.add(word.query(this.setup));
        }
        return Optional.of(
            Query.of(
                QueryOp.OR,
                Query.of(
                    QueryOp.SYNONYM,
                    List.of(
                        Query.wildcard(
                            Wildcard.of(word.name())
                                .withExpansion(Terms.PARTIAL)
                                .withCombiner(QueryOp.OR)
                        )
                    )
                ),
                Query.of(QueryOp.SYNONYM, full)
            )
        );
    }

    private Optional<Query> window(
        final QueryOp oper, final List<Word> words, final int delta
    ) {
        final List<Query> parts = new ArrayList<>(words.size());
        for (final Word word : words) {
            parts.add(word.positional().query(this.setup));
        }
        final Query query;
        if (this.setup.has(ParseFlag.NO_POSITIONS)) {
            query = Query.of(QueryOp.AND, parts);
        } else {
            query = Query.of(oper, parts, delta + parts.size());
        }
        return Optional.of(query);
    }

    private List<Query> members(
        final List<Word> words, final boolean positional, final boolean stop
    ) {
        final List<Query> parts = new ArrayList<>(words.size());
        for (final Word word : words) {
            if (!stop || !this.setup.stopped(word)) {
                Word used = word;
                if (positional) {
                    used = word.positional();
                }
                parts.add(used.query(this.setup));
            }
        }
        return parts;
    }

    /**
     * Words separated by white space.
     *
     * @param words Words in order
     * @param empty True when the group may be empty after stopping
     * @since 0.1.0
     */
    record Group(List<Word> words, boolean empty) {
    }
}
