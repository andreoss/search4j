/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser tokeniser.
 */
package io.github.andreoss.search4j.core.queryparser;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokeniser for query strings.
 *
 * <p>Produces a stream of {@link QueryParserToken} from a query string,
 * handling terms, phrases, operators, wildcards, ranges, and field prefixes.</p>
 *
 * @since 0.1.0
 */
public final class QueryParserTokeniser {

    private final String input;
    private int position;
    private final List<QueryParserToken> tokens;

    /**
     * Creates a tokeniser for a query string.
     *
     * @param query Query string to tokenise
     */
    public QueryParserTokeniser(final String query) {
        this.input = query;
        this.position = 0;
        this.tokens = new ArrayList<>();
    }

    /**
     * Tokenises the entire query string.
     *
     * @return List of tokens
     */
    public List<QueryParserToken> tokenise() {
        this.tokens.clear();
        this.position = 0;

        while (this.position < this.input.length()) {
            this.skipWhitespace();

            if (this.position >= this.input.length()) {
                break;
            }

            this.tokeniseNext();
        }

        this.tokens.add(QueryParserToken.EOF.INSTANCE);
        return List.copyOf(this.tokens);
    }

    private void skipWhitespace() {
        while (this.position < this.input.length()
            && Character.isWhitespace(this.input.charAt(this.position))) {
            this.position++;
        }
    }

    private void tokeniseNext() {
        final char c = this.input.charAt(this.position);

        switch (c) {
            case '"' -> this.tokenisePhrase();
            case '(' -> this.addToken(new QueryParserToken.GroupStart(this.position++));
            case ')' -> this.addToken(new QueryParserToken.GroupEnd(this.position++));
            case '[' -> this.tokeniseRange();
            case '+' -> this.tokeniseOperator("+");
            case '-' -> this.tokeniseOperator("-");
            case '~' -> this.tokeniseOperator("~");
            default -> {
                if (c == 'A' || c == 'O' || c == 'N') {
                    this.tokeniseOperator();
                } else if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '*' || c == '?') {
                    this.tokeniseTermOrPrefix();
                } else {
                    // Skip unknown character
                    this.position++;
                }
            }
        }
    }

    private void tokenisePhrase() {
        this.position++; // Skip opening quote
        final int start = this.position;
        final List<String> terms = new ArrayList<>();

        while (this.position < this.input.length()
            && this.input.charAt(this.position) != '"') {
            this.skipWhitespace();
            final int termStart = this.position;
            while (this.position < this.input.length()
                && this.input.charAt(this.position) != '"'
                && !Character.isWhitespace(this.input.charAt(this.position))) {
                this.position++;
            }
            if (this.position > termStart) {
                terms.add(this.input.substring(termStart, this.position));
            }
            this.skipWhitespace();
        }

        if (this.position < this.input.length()) {
            this.position++; // Skip closing quote
        }

        this.addToken(new QueryParserToken.Phrase(
            terms.toArray(new String[0]), start));
    }

    private void tokeniseRange() {
        this.position++; // Skip opening bracket
        this.skipWhitespace();

        final int lowerStart = this.position;
        while (this.position < this.input.length()
            && this.input.charAt(this.position) != 'T'
            && this.input.charAt(this.position) != ']'
            && !Character.isWhitespace(this.input.charAt(this.position))) {
            this.position++;
        }
        final String lower = this.input.substring(lowerStart, this.position).trim();

        this.skipWhitespace();
        // Expect "TO"
        if (this.position + 1 < this.input.length()
            && this.input.charAt(this.position) == 'T'
            && this.input.charAt(this.position + 1) == 'O') {
            this.position += 2;
        }

        this.skipWhitespace();

        final int upperStart = this.position;
        while (this.position < this.input.length()
            && this.input.charAt(this.position) != ']') {
            this.position++;
        }
        final String upper = this.input.substring(upperStart, this.position).trim();

        boolean inclusive = true;
        if (this.position < this.input.length()
            && this.input.charAt(this.position) == '}') {
            inclusive = false;
            this.position++;
        } else if (this.position < this.input.length()) {
            this.position++; // Skip closing bracket
        }

        this.addToken(new QueryParserToken.Range(lower, upper, inclusive, lowerStart));
    }

    private void tokeniseWildcard() {
        final int start = this.position;
        final StringBuilder pattern = new StringBuilder();

        while (this.position < this.input.length()
            && (this.input.charAt(this.position) == '*'
                || this.input.charAt(this.position) == '?')) {
            pattern.append(this.input.charAt(this.position));
            this.position++;
        }

        // Collect the rest of the term
        while (this.position < this.input.length()
            && !Character.isWhitespace(this.input.charAt(this.position))
            && this.input.charAt(this.position) != ')'
            && this.input.charAt(this.position) != ']') {
            pattern.append(this.input.charAt(this.position));
            this.position++;
        }

        this.addToken(new QueryParserToken.Wildcard(pattern.toString(), start));
    }

    private void tokeniseOperator() {
        this.tokeniseOperator(null);
    }

    private void tokeniseOperator(final String explicit) {
        final int start = this.position;
        String op;

        if (explicit != null) {
            op = explicit;
            this.position++;
        } else {
            // Try to match AND, OR, NOT
            if (this.matchKeyword("AND")) {
                op = "AND";
            } else if (this.matchKeyword("OR")) {
                op = "OR";
            } else if (this.matchKeyword("NOT")) {
                op = "NOT";
            } else {
                op = "";
            }
        }

        if (!op.isEmpty()) {
            final QueryParserToken.Type type;
            switch (op) {
                case "AND" -> type = QueryParserToken.Type.OPERATOR;
                case "OR" -> type = QueryParserToken.Type.OPERATOR;
                case "NOT" -> type = QueryParserToken.Type.OPERATOR;
                case "+" -> type = QueryParserToken.Type.OPERATOR;
                case "-" -> type = QueryParserToken.Type.OPERATOR;
                case "~" -> type = QueryParserToken.Type.OPERATOR;
                default -> type = QueryParserToken.Type.TERM;
            }
            // For simplicity, treat all as OPERATOR tokens
            this.addToken(new QueryParserToken.Operator(null, start));
        }
    }

    private boolean matchKeyword(final String keyword) {
        if (this.position + keyword.length() > this.input.length()) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            final char c1 = this.input.charAt(this.position + i);
            final char c2 = keyword.charAt(i);
            if (Character.toUpperCase(c1) != c2) {
                return false;
            }
        }
        // Check word boundary
        final int next = this.position + keyword.length();
        if (next < this.input.length()) {
            final char nextChar = this.input.charAt(next);
            if (Character.isLetterOrDigit(nextChar) || nextChar == '_') {
                return false;
            }
        }
        this.position = next;
        return true;
    }

    private void tokeniseTermOrPrefix() {
        final int start = this.position;
        final StringBuilder term = new StringBuilder();

        while (this.position < this.input.length()) {
            final char c = this.input.charAt(this.position);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '*' || c == '?') {
                term.append(c);
                this.position++;
            } else if (c == ':' && term.length() > 0) {
                // Field prefix
                this.position++;
                this.addToken(new QueryParserToken.Prefix(term.toString(), start));
                this.tokeniseTermOrValue();
                return;
            } else {
                break;
            }
        }

        final String termStr = term.toString();
        if (!termStr.isEmpty()) {
            // Check for wildcard
            if (termStr.contains("*") || termStr.contains("?")) {
                this.addToken(new QueryParserToken.Wildcard(termStr, start));
            } else {
                this.addToken(new QueryParserToken.Term(termStr, start));
            }
        }
    }

    private void tokeniseTermOrValue() {
        this.skipWhitespace();
        final int start = this.position;
        final StringBuilder value = new StringBuilder();

        while (this.position < this.input.length()
            && !Character.isWhitespace(this.input.charAt(this.position))
            && this.input.charAt(this.position) != ')'
            && this.input.charAt(this.position) != ']') {
            value.append(this.input.charAt(this.position));
            this.position++;
        }

        if (value.length() > 0) {
            this.addToken(new QueryParserToken.Term(value.toString(), start));
        }
    }

    private void addToken(final QueryParserToken token) {
        this.tokens.add(token);
    }
}
