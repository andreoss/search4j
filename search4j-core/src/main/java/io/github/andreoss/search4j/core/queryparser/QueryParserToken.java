/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser tokens and token types.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;

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
    QueryParserToken.End {

    /**
     * Token type enumeration.
     *
     * @since 0.1.0
     */
    enum Type {

        /**
         * A single term.
         */
        TERM,

        /**
         * A quoted phrase.
         */
        PHRASE,

        /**
         * A boolean, positional or unary operator.
         */
        OPERATOR,

        /**
         * A field name before an operator or a value.
         */
        PREFIX,

        /**
         * A wildcard pattern.
         */
        WILDCARD,

        /**
         * A value range.
         */
        RANGE,

        /**
         * An opening parenthesis.
         */
        GROUP_START,

        /**
         * A closing parenthesis.
         */
        GROUP_END,

        /**
         * A field name and its value.
         */
        FIELD_SPEC,

        /**
         * The end of the input.
         */
        END
    }

    /**
     * Token type.
     *
     * @return Type of this token
     */
    Type type();

    /**
     * A single term token.
     *
     * @param text Term text
     * @param position Offset of the term in the query
     * @since 0.1.0
     */
    record Term(String text, int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.TERM;
        }
    }

    /**
     * A quoted phrase token.
     *
     * @param terms Words of the phrase
     * @param position Offset of the opening quote in the query
     * @since 0.1.0
     */
    record Phrase(List<String> terms, int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.PHRASE;
        }
    }

    /**
     * An operator token.
     *
     * @param op Operator
     * @param position Offset of the operator in the query
     * @since 0.1.0
     */
    record Operator(QueryOp op, int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.OPERATOR;
        }
    }

    /**
     * A field name before a value.
     *
     * @param field Field name
     * @param position Offset of the field name in the query
     * @since 0.1.0
     */
    record Prefix(String field, int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.PREFIX;
        }
    }

    /**
     * A wildcard pattern token.
     *
     * @param pattern Pattern with its glob characters
     * @param position Offset of the pattern in the query
     * @since 0.1.0
     */
    record Wildcard(String pattern, int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.WILDCARD;
        }
    }

    /**
     * A value range token.
     *
     * @param lower Lower bound
     * @param upper Upper bound
     * @param bounds Kind of the opening bound
     * @param closing Closing bracket, a square or a curly one
     * @param position Offset of the opening bracket in the query
     * @since 0.1.0
     */
    record Range(
        String lower, String upper, Range.Bounds bounds, char closing,
        int position
    ) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.RANGE;
        }

        /**
         * Reports whether both bounds are inclusive.
         *
         * @return True when the range includes its bounds
         */
        public boolean inclusive() {
            return this.bounds == Range.Bounds.INCLUSIVE && this.closing == ']';
        }

        /**
         * Kind of a range bound.
         *
         * @since 0.1.0
         */
        enum Bounds {

            /**
             * The bound belongs to the range.
             */
            INCLUSIVE,

            /**
             * The bound does not belong to the range.
             */
            EXCLUSIVE
        }
    }

    /**
     * An opening parenthesis token.
     *
     * @param position Offset of the parenthesis in the query
     * @since 0.1.0
     */
    record GroupStart(int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.GROUP_START;
        }
    }

    /**
     * A closing parenthesis token.
     *
     * @param position Offset of the parenthesis in the query
     * @since 0.1.0
     */
    record GroupEnd(int position) implements QueryParserToken {

        @Override
        public Type type() {
            return Type.GROUP_END;
        }
    }

    /**
     * A field name and its value.
     *
     * @param field Field name
     * @param value Field value
     * @param position Offset of the field name in the query
     * @since 0.1.0
     */
    record FieldSpec(String field, String value, int position)
        implements QueryParserToken {

        @Override
        public Type type() {
            return Type.FIELD_SPEC;
        }
    }

    /**
     * The end-of-input token.
     *
     * @since 0.1.0
     */
    enum End implements QueryParserToken {

        /**
         * The single end-of-input token.
         */
        INSTANCE;

        @Override
        public Type type() {
            return Type.END;
        }
    }
}
