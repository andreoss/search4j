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
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a query from tokens; operators from lowest to highest precedence
 * are OR, XOR, AND and NOT, and SYN, above groups of words.
 *
 * @since 0.1.0
 */
final class Grammar {

    /**
     * Message of a syntax error that allows a second parse.
     */
    static final String FAILURE = "parse error";

    /**
     * Tokens that start a group of words.
     */
    private static final Set<Lexeme.Kind> STARTS = Set.of(
        Lexeme.Kind.TERM, Lexeme.Kind.WILD_TERM, Lexeme.Kind.PARTIAL_TERM,
        Lexeme.Kind.QUOTE, Lexeme.Kind.BRA, Lexeme.Kind.SYNONYM,
        Lexeme.Kind.LOVE, Lexeme.Kind.HATE
    );

    /**
     * Binary operator tokens.
     */
    private static final Set<Lexeme.Kind> BINARY = Set.of(
        Lexeme.Kind.AND, Lexeme.Kind.OR, Lexeme.Kind.NOT, Lexeme.Kind.XOR,
        Lexeme.Kind.SYN
    );

    /**
     * Tokens to parse.
     */
    private final List<Lexeme> tokens;

    /**
     * Parse settings.
     */
    private final Setup setup;

    /**
     * Builder of compound terms.
     */
    private final Terms terms;

    /**
     * Index of the next token.
     */
    private int index;

    /**
     * Creates a grammar over tokens.
     *
     * @param tokens Tokens ending with the end token
     * @param setup Parse settings
     */
    Grammar(final List<Lexeme> tokens, final Setup setup) {
        this.tokens = tokens;
        this.setup = setup;
        this.terms = new Terms(setup);
    }

    /**
     * Parses every token.
     *
     * @return Query, match-nothing when no word remains
     */
    Query parse() {
        final Optional<Query> query = this.disjunction();
        if (this.kind() != Lexeme.Kind.END) {
            throw new QueryParserError(Grammar.FAILURE);
        }
        return query.orElse(Query.MATCH_NOTHING);
    }

    private Optional<Query> disjunction() {
        Optional<Query> left = this.exclusive();
        while (this.kind() == Lexeme.Kind.OR) {
            this.next();
            final Optional<Query> right = this.exclusive();
            left = Grammar.vet(left, QueryOp.OR, right, "OR");
        }
        return left;
    }

    private Optional<Query> exclusive() {
        Optional<Query> left = this.conjunction();
        while (this.kind() == Lexeme.Kind.XOR) {
            this.next();
            final Optional<Query> right = this.conjunction();
            left = Grammar.vet(left, QueryOp.XOR, right, "XOR");
        }
        return left;
    }

    private Optional<Query> conjunction() {
        Optional<Query> left = this.synonyms();
        boolean more = true;
        while (more) {
            if (this.kind() == Lexeme.Kind.AND) {
                this.next();
                left = this.after(left);
            } else if (this.kind() == Lexeme.Kind.NOT) {
                this.next();
                final Optional<Query> right = this.synonyms();
                Optional<Query> base = left;
                if (base.isEmpty() && this.setup.has(ParseFlag.PURE_NOT)) {
                    base = Optional.of(
                        Query.scale(
                            0.0,
                            Query.term("", new TermCount(1), TermPos.FIRST)
                        )
                    );
                }
                left = Grammar.vet(base, QueryOp.AND_NOT, right, "NOT");
            } else {
                more = false;
            }
        }
        return left;
    }

    private Optional<Query> after(final Optional<Query> left) {
        final Optional<Query> result;
        if (this.kind() == Lexeme.Kind.NOT) {
            this.next();
            result = Grammar.vet(
                left, QueryOp.AND_NOT, this.synonyms(), "AND NOT"
            );
        } else if (this.kind() == Lexeme.Kind.HATE_AFTER_AND) {
            this.next();
            result = Grammar.vet(left, QueryOp.AND_NOT, this.synonyms(), "AND");
        } else {
            result = Grammar.vet(left, QueryOp.AND, this.synonyms(), "AND");
        }
        return result;
    }

    private Optional<Query> synonyms() {
        Optional<Query> left = this.group();
        while (this.kind() == Lexeme.Kind.SYN) {
            this.next();
            final Optional<Query> right = this.group();
            left = Grammar.vet(left, QueryOp.SYNONYM, right, "SYN");
        }
        return left;
    }

    private Optional<Query> group() {
        final Optional<Query> result;
        final Lexeme.Kind first = this.kind();
        if (first == Lexeme.Kind.LOVE || first == Lexeme.Kind.HATE) {
            result = this.prob(new Prob(this.setup));
        } else if (Grammar.STARTS.contains(first)) {
            result = this.started();
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private Optional<Query> started() {
        final Piece piece = this.piece();
        final Optional<Query> result;
        if (this.continues()) {
            result = this.prob(this.opening(piece));
        } else {
            result = piece.query();
        }
        return result;
    }

    private Prob opening(final Piece piece) {
        final Prob prob = new Prob(this.setup);
        if (this.kind() == Lexeme.Kind.LOVE || this.kind() == Lexeme.Kind.HATE) {
            prob.single(this.stop(piece));
        } else {
            prob.pair(this.stop(piece), this.stop(this.piece()));
        }
        return prob;
    }

    private Optional<Query> prob(final Prob prob) {
        while (this.continues()) {
            if (this.kind() == Lexeme.Kind.LOVE) {
                this.next();
                prob.require(this.piece().query());
            } else if (this.kind() == Lexeme.Kind.HATE) {
                this.next();
                prob.exclude(this.piece().query());
            } else {
                prob.add(this.stop(this.piece()));
            }
        }
        return prob.build();
    }

    private boolean continues() {
        return Grammar.STARTS.contains(this.kind());
    }

    private Optional<Query> stop(final Piece piece) {
        final Optional<Query> result;
        if (piece.bare() && this.setup.stopped(piece.word())) {
            result = Optional.empty();
        } else {
            result = piece.query();
        }
        return result;
    }

    private Piece piece() {
        final Lexeme token = this.next();
        return switch (token.kind()) {
            case TERM -> this.word(token.word());
            case WILD_TERM -> Piece.of(Terms.wildcard(token.word()));
            case PARTIAL_TERM -> Piece.of(this.terms.partial(token.word()));
            case QUOTE -> Piece.of(this.terms.phrase(this.quoted()));
            case BRA -> Piece.of(this.bracket());
            case SYNONYM -> this.synonym();
            default -> throw new QueryParserError(Grammar.FAILURE);
        };
    }

    private Piece word(final Word word) {
        return switch (this.kind()) {
            case PHR_TERM -> Piece.of(
                this.terms.phrase(this.collect(word, Lexeme.Kind.PHR_TERM))
            );
            case GROUP_TERM -> Piece.of(this.terms.group(this.groupOf(word)));
            case NEAR, ADJ -> Piece.of(this.proximity(word));
            default -> new Piece(word, Optional.of(word.query(this.setup)), true);
        };
    }

    private Piece synonym() {
        final Lexeme token = this.next();
        final Piece result;
        if (token.kind() == Lexeme.Kind.TERM) {
            result = Piece.of(Optional.of(token.word().query(this.setup)));
        } else if (token.kind() == Lexeme.Kind.QUOTE) {
            result = Piece.of(
                Optional.of(
                    Query.of(
                        QueryOp.SYNONYM,
                        List.of(this.terms.phrase(this.quoted()).orElseThrow())
                    )
                )
            );
        } else {
            throw new QueryParserError(Grammar.FAILURE);
        }
        return result;
    }

    private Optional<Query> bracket() {
        if (!this.continues() && !Grammar.BINARY.contains(this.kind())) {
            throw new QueryParserError(Grammar.FAILURE);
        }
        final Optional<Query> inner = this.disjunction();
        if (this.next().kind() != Lexeme.Kind.KET) {
            throw new QueryParserError(Grammar.FAILURE);
        }
        return inner;
    }

    private List<Word> quoted() {
        if (this.kind() != Lexeme.Kind.TERM) {
            throw new QueryParserError(Grammar.FAILURE);
        }
        final List<Word> words = new ArrayList<>(4);
        while (this.kind() == Lexeme.Kind.TERM) {
            words.add(this.next().word());
        }
        if (this.next().kind() != Lexeme.Kind.QUOTE) {
            throw new QueryParserError(Grammar.FAILURE);
        }
        return words;
    }

    private List<Word> collect(final Word first, final Lexeme.Kind kind) {
        final List<Word> words = new ArrayList<>(4);
        words.add(first);
        while (this.kind() == kind) {
            words.add(this.next().word());
        }
        return words;
    }

    private Terms.Group groupOf(final Word first) {
        final List<Word> words = new ArrayList<>(4);
        words.add(first);
        boolean empty = false;
        while (this.kind() == Lexeme.Kind.GROUP_TERM
            || this.kind() == Lexeme.Kind.EMPTY_GROUP_OK) {
            final Lexeme token = this.next();
            if (token.kind() == Lexeme.Kind.GROUP_TERM) {
                words.add(token.word());
            } else {
                empty = true;
            }
        }
        return new Terms.Group(words, empty);
    }

    private Optional<Query> proximity(final Word first) {
        final Lexeme.Kind kind = this.kind();
        final List<Word> words = new ArrayList<>(4);
        words.add(first);
        int window = 0;
        while (this.kind() == kind) {
            window = Grammar.wider(window, this.next().width());
            if (this.kind() != Lexeme.Kind.TERM) {
                throw new QueryParserError(Grammar.FAILURE);
            }
            words.add(this.next().word());
        }
        return this.terms.proximity(words, kind == Lexeme.Kind.NEAR, window);
    }

    private static Optional<Query> vet(
        final Optional<Query> left, final QueryOp oper,
        final Optional<Query> right, final String text
    ) {
        if (left.isEmpty() || right.isEmpty()) {
            throw new QueryParserError(
                String.format("Syntax: <expression> %s <expression>", text)
            );
        }
        return Optional.of(Joins.join(left.get(), oper, right.get()));
    }

    private static int wider(final int window, final int width) {
        final int widest;
        if (Integer.compareUnsigned(width, window) > 0) {
            widest = width;
        } else {
            widest = window;
        }
        return widest;
    }

    private Lexeme.Kind kind() {
        return this.tokens.get(this.index).kind();
    }

    private Lexeme next() {
        final Lexeme token = this.tokens.get(this.index);
        if (token.kind() != Lexeme.Kind.END) {
            this.index = this.index + 1;
        }
        return token;
    }

    /**
     * A parsed word or compound term.
     *
     * @param word The word of a bare term, blank otherwise
     * @param query The query, absent when every word was stopped
     * @param bare True for a single word subject to stopping
     * @since 0.1.0
     */
    private record Piece(Word word, Optional<Query> query, boolean bare) {

        /**
         * A compound term.
         *
         * @param query Its query
         * @return Piece
         */
        static Piece of(final Optional<Query> query) {
            return new Piece(Lexeme.BLANK, query, false);
        }
    }
}
