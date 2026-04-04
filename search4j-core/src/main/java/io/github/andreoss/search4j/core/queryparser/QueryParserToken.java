/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser tokens and token types.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * A token produced by the query parser tokeniser.
 *
 * @since 0.1.0
 */
public sealed interface QueryParserToken permits
    QueryParserToken.Term,
    QueryParserToken.Phrase,
    QueryParserToken.Operator,
    QueryParserToken.Prefix,
    QueryParserToken.Wildcard,
    QueryParserToken.Range,
    QueryParserToken.GroupStart,
    QueryParserToken.GroupEnd,
    QueryParserToken.FieldSpec,
    QueryParserToken.EOF {

    /**
     * Token type enumeration.
     */
    enum Type {
        TERM,
        PHRASE,
        OPERATOR,
        PREFIX,
        WILDCARD,
        RANGE,
        GROUP_START,
        GROUP_END,
        FIELD_SPEC,
        EOF
    }

    /**
     * Returns the token type.
     */
    Type type();

    /**
     * A single term token.
     */
    record Term(String text, int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.TERM;
        }
    }

    /**
     * A phrase token (quoted sequence of terms).
     */
    record Phrase(String[] terms, int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.PHRASE;
        }
    }

    /**
     * An operator token (AND, OR, NOT, etc.).
     */
    record Operator(QueryOp op, int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.OPERATOR;
        }
    }

    /**
     * A field prefix token (e.g., "title:").
     */
    record Prefix(String field, int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.PREFIX;
        }
    }

    /**
     * A wildcard token (term with * or ?).
     */
    record Wildcard(String pattern, int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.WILDCARD;
        }
    }

    /**
     * A range token (e.g., [a TO z]).
     */
    record Range(String lower, String upper, boolean inclusive, int position)
        implements QueryParserToken {
        @Override
        public Type type() {
            return Type.RANGE;
        }
    }

    /**
     * Group start token (parenthesis).
     */
    record GroupStart(int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.GROUP_START;
        }
    }

    /**
     * Group end token (parenthesis).
     */
    record GroupEnd(int position) implements QueryParserToken {
        @Override
        public Type type() {
            return Type.GROUP_END;
        }
    }

    /**
     * Field specification token (e.g., "field:value").
     */
    record FieldSpec(String field, String value, int position)
        implements QueryParserToken {
        @Override
        public Type type() {
            return Type.FIELD_SPEC;
        }
    }

    /**
     * End of input token.
     */
    enum EOF implements QueryParserToken {
        INSTANCE;

        @Override
        public Type type() {
            return Type.EOF;
        }
    }
}
