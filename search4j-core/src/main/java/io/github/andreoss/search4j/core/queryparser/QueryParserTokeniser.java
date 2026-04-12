/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser tokeniser.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tokeniser for query strings.
 *
 * <p>Produces a stream of {@link QueryParserToken} from a query string,
 * handling terms, phrases, operators, wildcards, ranges and field
 * prefixes.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnnecessaryLocalRule")
public final class QueryParserTokeniser {

    /**
     * Operator that requires the following term.
     */
    private static final char PLUS = '+';

    /**
     * Operator that excludes the following term.
     */
    private static final char MINUS = '-';

    /**
     * Proximity operator.
     */
    private static final char TILDE = '~';

    /**
     * Query string being tokenised.
     */
    private final String input;

    /**
     * Tokens produced so far.
     */
    private final List<QueryParserToken> tokens;

    /**
     * Offset of the next character to read.
     */
    private int pos;

    /**
     * Creates a tokeniser for a query string.
     *
     * @param query Query string to tokenise
     */
    public QueryParserTokeniser(final String query) {
        this.input = query;
        this.tokens = new ArrayList<>(8);
        this.pos = 0;
    }

    /**
     * Tokenises the entire query string.
     *
     * @return Tokens ending with the end-of-input token
     */
    public List<QueryParserToken> tokenise() {
        this.tokens.clear();
        this.pos = 0;
        this.skip();
        while (this.pos < this.input.length()) {
            this.next();
            this.skip();
        }
        this.tokens.add(QueryParserToken.End.INSTANCE);
        return List.copyOf(this.tokens);
    }

    private void skip() {
        while (
            this.pos < this.input.length()
                && Character.isWhitespace(this.input.charAt(this.pos))
        ) {
            this.pos = this.pos + 1;
        }
    }

    private void next() {
        final char chr = this.input.charAt(this.pos);
        switch (chr) {
            case '"' -> this.phrase();
            case '[' -> this.range(QueryParserToken.Range.Bounds.INCLUSIVE);
            case '{' -> this.range(QueryParserToken.Range.Bounds.EXCLUSIVE);
            case '(' -> this.group(new QueryParserToken.GroupStart(this.pos));
            case ')' -> this.group(new QueryParserToken.GroupEnd(this.pos));
            case QueryParserTokeniser.PLUS -> QueryParserTokeniser
                .operator(this, QueryOp.AND, QueryParserTokeniser.PLUS);
            case QueryParserTokeniser.MINUS -> QueryParserTokeniser
                .operator(this, QueryOp.AND_NOT, QueryParserTokeniser.MINUS);
            case QueryParserTokeniser.TILDE -> QueryParserTokeniser
                .operator(this, QueryOp.NEAR, QueryParserTokeniser.TILDE);
            default -> {
                if (QueryParserTokeniser.word(chr)) {
                    this.term();
                } else {
                    this.pos = this.pos + 1;
                }
            }
        }
    }

    private static boolean word(final char chr) {
        return Character.isLetterOrDigit(chr)
            || QueryParserTokeniser.point(chr)
            || QueryParserTokeniser.glob(chr);
    }

    private static boolean glob(final char chr) {
        return chr == '-' || chr == '*' || chr == '?';
    }

    private static boolean point(final char chr) {
        return chr == '_' || chr == '.';
    }

    private void group(final QueryParserToken token) {
        this.pos = this.pos + 1;
        this.tokens.add(token);
    }

    private void phrase() {
        final int start = this.pos;
        this.pos = this.pos + 1;
        final List<String> terms = new ArrayList<>(2);
        final StringBuilder part = new StringBuilder();
        boolean done = false;
        while (this.pos < this.input.length() && !done) {
            final char chr = this.input.charAt(this.pos);
            if (chr == '"') {
                done = true;
                this.pos = this.pos + 1;
            } else if (chr == '\\' && this.pos + 1 < this.input.length()) {
                part.append(this.input.charAt(this.pos + 1));
                this.pos = this.pos + 2;
            } else if (Character.isWhitespace(chr)) {
                QueryParserTokeniser.flush(part, terms);
                this.pos = this.pos + 1;
            } else {
                part.append(chr);
                this.pos = this.pos + 1;
            }
        }
        if (!done) {
            throw new IllegalArgumentException("Unclosed quoted phrase");
        }
        QueryParserTokeniser.flush(part, terms);
        this.tokens.add(
            new QueryParserToken.Phrase(List.copyOf(terms), start)
        );
    }

    private static void flush(final StringBuilder part, final List<String> terms) {
        if (!part.isEmpty()) {
            terms.add(part.toString());
            part.setLength(0);
        }
    }

    private void range(final QueryParserToken.Range.Bounds bounds) {
        final int start = this.pos;
        final int close = this.closing(this.pos + 1);
        if (close < 0) {
            throw new IllegalArgumentException("Unclosed range");
        }
        final int split = this.separator(this.pos + 1, close);
        if (split < 0) {
            throw new IllegalArgumentException("Range requires a TO separator");
        }
        final String lower = this.input.substring(this.pos + 1, split).trim();
        final String upper = this.input.substring(split + 4, close).trim();
        this.pos = close + 1;
        this.tokens.add(
            new QueryParserToken.Range(
                lower, upper, bounds, this.input.charAt(close), start
            )
        );
    }

    private int closing(final int from) {
        int found = -1;
        int offset = from;
        while (offset < this.input.length() && found < 0) {
            final char chr = this.input.charAt(offset);
            if (chr == ']' || chr == '}') {
                found = offset;
            } else {
                offset = offset + 1;
            }
        }
        return found;
    }

    private int separator(final int from, final int bound) {
        final String upper = this.input.toUpperCase(Locale.ROOT);
        final int found = upper.indexOf(" TO ", from);
        final int result;
        if (found < 0 || found >= bound) {
            result = -1;
        } else {
            result = found;
        }
        return result;
    }

    private static void operator(
        final QueryParserTokeniser tokeniser, final QueryOp oper,
        final char chr
    ) {
        final int start = tokeniser.pos;
        tokeniser.pos = tokeniser.pos + 1;
        final int parameter;
        if (chr == QueryParserTokeniser.TILDE) {
            parameter = tokeniser.distance();
        } else {
            parameter = 0;
        }
        tokeniser.tokens.add(
            new QueryParserToken.Operator(oper, start, parameter)
        );
    }

    private int distance() {
        final int from = this.pos;
        while (
            this.pos < this.input.length()
                && Character.isDigit(this.input.charAt(this.pos))
        ) {
            this.pos = this.pos + 1;
        }
        final int value;
        if (this.pos > from) {
            value = Integer.parseInt(this.input.substring(from, this.pos));
        } else {
            value = 0;
        }
        return value;
    }

    private void term() {
        final int start = this.pos;
        while (
            this.pos < this.input.length()
                && QueryParserTokeniser.word(this.input.charAt(this.pos))
        ) {
            this.pos = this.pos + 1;
        }
        final String word = this.input.substring(start, this.pos);
        this.dispatch(word, start);
    }

    private void dispatch(final String word, final int start) {
        if (this.positionalSlash(word)) {
            this.slashWindow(word, start);
        } else if (QueryParserTokeniser.positional(word)) {
            this.tokens.add(
                new QueryParserToken.Operator(
                    QueryParserTokeniser.operator(word), start
                )
            );
        } else if (this.fieldDelimiter()) {
            this.pos = this.pos + 1;
            this.field(word, start);
        } else if (QueryParserTokeniser.wildcard(word)) {
            this.tokens.add(new QueryParserToken.Wildcard(word, start));
        } else {
            this.tokens.add(QueryParserTokeniser.wordToken(word, start));
        }
    }

    private boolean positionalSlash(final String word) {
        return this.pos < this.input.length()
            && this.input.charAt(this.pos) == '/'
            && QueryParserTokeniser.positional(word);
    }

    private void slashWindow(final String word, final int start) {
        this.pos = this.pos + 1;
        final int parameter = this.distance();
        if (parameter == 0
            || this.pos < this.input.length()
                && !Character.isWhitespace(this.input.charAt(this.pos))) {
            throw new IllegalArgumentException("Invalid positional distance");
        }
        this.tokens.add(
            new QueryParserToken.Operator(
                QueryParserTokeniser.operator(word), start, parameter
            )
        );
    }

    private boolean fieldDelimiter() {
        return this.pos < this.input.length()
            && this.input.charAt(this.pos) == ':';
    }

    private static boolean wildcard(final String word) {
        return word.indexOf('*') >= 0 || word.indexOf('?') >= 0;
    }

    private static boolean positional(final String word) {
        return "NEAR".equals(word) || "ADJ".equals(word);
    }

    private static QueryOp operator(final String word) {
        final QueryOp result;
        if ("NEAR".equals(word)) {
            result = QueryOp.NEAR;
        } else {
            result = QueryOp.PHRASE;
        }
        return result;
    }

    private static QueryParserToken wordToken(final String word, final int start) {
        final QueryParserToken token;
        if ("AND".equals(word)) {
            token = new QueryParserToken.Operator(QueryOp.AND, start);
        } else if ("OR".equals(word)) {
            token = new QueryParserToken.Operator(QueryOp.OR, start);
        } else if ("NOT".equals(word)) {
            token = new QueryParserToken.Operator(QueryOp.AND_NOT, start);
        } else {
            token = new QueryParserToken.Term(word, start);
        }
        return token;
    }

    private void field(final String name, final int start) {
        if (this.pos < this.input.length() && this.input.charAt(this.pos) == '[') {
            this.tokens.add(new QueryParserToken.Prefix(name, start));
            this.range(QueryParserToken.Range.Bounds.INCLUSIVE);
        } else {
            final int from = this.pos;
            while (
                this.pos < this.input.length()
                    && !Character.isWhitespace(this.input.charAt(this.pos))
                    && this.input.charAt(this.pos) != ')'
            ) {
                this.pos = this.pos + 1;
            }
            final String value = this.input.substring(from, this.pos);
            this.tokens.add(new QueryParserToken.FieldSpec(name, value, start));
        }
    }
}
