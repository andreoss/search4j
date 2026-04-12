/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Recursive descent parser for query strings.
 *
 * <p>Precedence from lowest to highest: OR, AND and AND_NOT, NEAR, phrase,
 * term.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnnecessaryLocalRule",
    "PMD.VariableCanBeInlined"
})
public final class QueryParser {

    /**
     * Token types that cannot start an operand.
     */
    private static final Set<QueryParserToken.Type> STOPS = Set.of(
        QueryParserToken.Type.GROUP_END, QueryParserToken.Type.OPERATOR,
        QueryParserToken.Type.PREFIX
    );

    /**
     * Token types that can end an operand.
     */
    private static final Set<QueryParserToken.Type> ENDS = Set.of(
        QueryParserToken.Type.TERM, QueryParserToken.Type.PHRASE,
        QueryParserToken.Type.WILDCARD, QueryParserToken.Type.GROUP_END,
        QueryParserToken.Type.RANGE, QueryParserToken.Type.FIELD_SPEC
    );

    /**
     * The tokens to parse.
     */
    private final List<QueryParserToken> tokens;

    /**
     * Default operator for implicit combinations.
     */
    private final QueryOp defop;

    /**
     * Current token index.
     */
    private int index;

    /**
     * Creates a parser for a token list.
     *
     * @param tokens Tokens to parse
     * @param defop Default operator for implicit AND/OR
     */
    public QueryParser(
        final List<QueryParserToken> tokens,
        final QueryOp defop
    ) {
        if (defop != QueryOp.AND && defop != QueryOp.OR) {
            throw new InvalidArgumentError(
                "Default operator must be AND or OR"
            );
        }
        this.tokens = tokens;
        this.defop = defop;
        this.index = 0;
    }

    /**
     * Parses the tokens into a Query.
     *
     * @return Parsed Query, or MATCH_NOTHING on empty input
     */
    public Query parse() {
        final Query query;
        if (this.peek().type() == QueryParserToken.Type.END) {
            query = Query.MATCH_NOTHING;
        } else {
            query = this.expression();
            if (this.peek().type() != QueryParserToken.Type.END) {
                throw new InvalidArgumentError(
                    "Unexpected tokens after query"
                );
            }
        }
        return query;
    }

    private Query expression() {
        return this.or();
    }

    private Query or() {
        final List<Query> parts = new ArrayList<>(2);
        parts.add(this.and());
        while (this.matches(QueryOp.OR)) {
            this.step();
            parts.add(this.and());
        }
        final Query query;
        if (parts.size() == 1) {
            query = parts.get(0);
        } else {
            query = Query.of(QueryOp.OR, parts);
        }
        return query;
    }

    private Query and() {
        final List<Query> parts = new ArrayList<>(2);
        parts.add(this.not());
        Query combined = null;
        boolean more = true;
        while (more) {
            final QueryOp oper = this.conjunction();
            if (oper == null) {
                more = false;
            } else if (oper == QueryOp.AND && combined == null) {
                parts.add(this.not());
            } else {
                combined = QueryParser.join(
                    combined, parts, oper, this.not()
                );
                parts.clear();
            }
        }
        return QueryParser.merge(combined, parts);
    }

    private static Query join(
        final Query combined, final List<Query> parts, final QueryOp oper,
        final Query right
    ) {
        final Query left = QueryParser.merge(combined, parts);
        final Query result;
        if (oper == QueryOp.AND) {
            result = left.and(right);
        } else if (oper == QueryOp.OR) {
            result = left.or(right);
        } else {
            result = left.andNot(right);
        }
        return result;
    }

    private static Query operand(final List<Query> parts) {
        final Query query;
        if (parts.size() == 1) {
            query = parts.get(0);
        } else {
            query = Query.of(QueryOp.AND, parts);
        }
        return query;
    }

    private static Query merge(final Query combined, final List<Query> parts) {
        final Query left;
        if (parts.isEmpty()) {
            left = combined;
        } else if (combined == null) {
            left = QueryParser.operand(parts);
        } else {
            left = combined.and(QueryParser.operand(parts));
        }
        return left;
    }

    private QueryOp conjunction() {
        final QueryOp result;
        if (this.matches(QueryOp.AND)) {
            this.step();
            result = QueryOp.AND;
        } else if (this.matches(QueryOp.AND_NOT)) {
            this.step();
            result = QueryOp.AND_NOT;
        } else if (this.adjacent()) {
            result = this.defop;
        } else {
            result = null;
        }
        return result;
    }

    private Query not() {
        final Query query;
        if (this.matches(QueryOp.AND_NOT)) {
            this.step();
            query = Query.MATCH_ALL.andNot(this.not());
        } else {
            query = this.near();
        }
        return query;
    }

    private Query near() {
        Query query = this.term();
        while (this.matches(QueryOp.NEAR) || this.matches(QueryOp.PHRASE)) {
            final QueryParserToken.Operator operator =
                (QueryParserToken.Operator) this.peek();
            this.step();
            final Query right = this.term();
            final int window;
            if (operator.parameter() == 0) {
                window = 11;
            } else {
                window = operator.parameter() + 1;
            }
            final Query positional;
            if (query.type() == QueryOp.LEAF_TERM
                && right.type() == QueryOp.LEAF_TERM) {
                positional = Query.of(
                    operator.op(),
                    List.of(
                        QueryParser.position(query, 1),
                        QueryParser.position(right, 2)
                    ),
                    window
                );
            } else {
                positional = Query.of(
                    operator.op(), List.of(query, right), window
                );
            }
            query = positional;
        }
        return query;
    }

    private static Query position(final Query query, final int position) {
        final Query result;
        if (query.type() == QueryOp.LEAF_TERM) {
            result = Query.term(
                query.node().term(), query.wqf(),
                new TermPos(position)
            );
        } else {
            result = query;
        }
        return result;
    }

    private Query term() {
        final QueryParserToken token = this.peek();
        final Query query;
        switch (token.type()) {
            case TERM -> {
                this.step();
                query = Query.term(((QueryParserToken.Term) token).text());
            }
            case PHRASE -> {
                this.step();
                query = QueryParser.phrase((QueryParserToken.Phrase) token);
            }
            case WILDCARD -> {
                this.step();
                query = Query.wildcard(
                    Wildcard.of(((QueryParserToken.Wildcard) token).pattern())
                );
            }
            case RANGE -> {
                this.step();
                query = QueryParser.range((QueryParserToken.Range) token);
            }
            case PREFIX -> query = this.prefixed();
            case GROUP_START -> {
                this.step();
                query = this.expression();
                this.expect(QueryParserToken.Type.GROUP_END);
                this.step();
            }
            case FIELD_SPEC -> {
                this.step();
                query = Query.term(
                    ((QueryParserToken.FieldSpec) token).value()
                );
            }
            case OPERATOR -> {
                this.step();
                query = this.term();
            }
            default -> throw new InvalidArgumentError(
                "Unexpected end of query"
            );
        }
        return query;
    }

    private static Query phrase(final QueryParserToken.Phrase token) {
        final List<String> words = token.terms();
        final Query query;
        if (words.isEmpty()) {
            query = Query.MATCH_NOTHING;
        } else if (words.size() == 1) {
            query = Query.term(words.get(0));
        } else {
            final List<Query> parts = new ArrayList<>(words.size());
            for (final String word : words) {
                parts.add(Query.term(word));
            }
            query = Query.of(QueryOp.PHRASE, parts, words.size());
        }
        return query;
    }

    private static Query range(final QueryParserToken.Range token) {
        return Query.wildcard(
            Wildcard.of(
                String.format("%s..%s", token.lower(), token.upper())
            )
        );
    }

    private Query prefixed() {
        final QueryParserToken.Prefix prefix =
            (QueryParserToken.Prefix) this.peek();
        this.step();
        final QueryParserToken next = this.peek();
        final Query query;
        if (next.type() == QueryParserToken.Type.TERM) {
            this.step();
            query = Query.term(
                String.format(
                    "%s:%s", prefix.field(),
                    ((QueryParserToken.Term) next).text()
                )
            );
        } else if (next.type() == QueryParserToken.Type.RANGE) {
            this.step();
            final QueryParserToken.Range range =
                (QueryParserToken.Range) next;
            query = Query.valueRange(
                new ValueNo(Integer.parseInt(prefix.field())),
                range.lower(), range.upper()
            );
        } else {
            throw new InvalidArgumentError(
                "Expected a term or a range after a field name"
            );
        }
        return query;
    }

    private QueryParserToken peek() {
        final QueryParserToken token;
        if (this.index < this.tokens.size()) {
            token = this.tokens.get(this.index);
        } else {
            token = QueryParserToken.End.INSTANCE;
        }
        return token;
    }

    private void step() {
        this.index = this.index + 1;
    }

    private void expect(final QueryParserToken.Type type) {
        if (this.peek().type() != type) {
            throw new InvalidArgumentError(
                "Expected a closing parenthesis"
            );
        }
    }

    private boolean matches(final QueryOp oper) {
        final QueryParserToken token = this.peek();
        return token.type() == QueryParserToken.Type.OPERATOR
            && ((QueryParserToken.Operator) token).op() == oper;
    }

    private boolean adjacent() {
        final QueryParserToken.Type current = this.peek().type();
        final boolean starts = QueryParser.starts(current)
            && this.index > 0 && QueryParser.ends(
                this.tokens.get(this.index - 1).type()
            );
        return starts;
    }

    private static boolean starts(final QueryParserToken.Type type) {
        return type != QueryParserToken.Type.END
            && !QueryParser.stops(type);
    }

    private static boolean stops(final QueryParserToken.Type type) {
        return QueryParser.STOPS.contains(type);
    }

    private static boolean ends(final QueryParserToken.Type type) {
        return QueryParser.ENDS.contains(type);
    }
}
