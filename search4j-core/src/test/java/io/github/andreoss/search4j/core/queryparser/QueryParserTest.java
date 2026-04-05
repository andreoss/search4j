/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query parser.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link QueryParser}.
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.UnnecessaryLocalRule", "PMD.UnitTestContainsTooManyAsserts"})
final class QueryParserTest {

    @Test
    void parsesASimpleTerm() {
        Assertions.assertEquals(
            QueryOp.LEAF_TERM, QueryParserTest.parse("hello").type(),
            "Leaf term"
        );
    }

    @Test
    void parsesTwoTermsAsAConjunction() {
        final Query query = QueryParserTest.parse("hello world");
        Assertions.assertEquals(
            QueryOp.AND, query.type(), "Implicit conjunction"
        );
        Assertions.assertEquals(2, query.subqueryCount(), "Two operands");
    }

    @Test
    void parsesAnExplicitConjunction() {
        final Query query = QueryParserTest.parse("hello AND world");
        Assertions.assertEquals(
            List.of(QueryOp.AND, 2),
            List.of(query.type(), query.subqueryCount()),
            "Explicit conjunction"
        );
    }

    @Test
    void parsesAUnion() {
        final Query query = QueryParserTest.parse("hello OR world");
        Assertions.assertEquals(
            List.of(QueryOp.OR, 2),
            List.of(query.type(), query.subqueryCount()),
            "Union of two terms"
        );
    }

    @Test
    void parsesADifference() {
        final Query query = QueryParserTest.parse("hello NOT world");
        Assertions.assertEquals(
            List.of(QueryOp.AND_NOT, 2),
            List.of(query.type(), query.subqueryCount()),
            "Difference of two terms"
        );
    }

    @Test
    void parsesAPhrase() {
        final Query query = QueryParserTest.parse("\"hello world\"");
        Assertions.assertEquals(
            List.of(QueryOp.PHRASE, 2),
            List.of(query.type(), query.subqueryCount()),
            "Phrase of two words"
        );
    }

    @Test
    void parsesAProximityExpression() {
        final Query query = QueryParserTest.parse("hello ~ world");
        Assertions.assertEquals(
            QueryOp.NEAR, query.type(), "Proximity operator"
        );
    }

    @Test
    void parsesAProximityDistance() {
        final Query query = QueryParserTest.parse("hello ~5 world");
        Assertions.assertEquals(
            QueryOp.NEAR, query.type(), "Proximity operator with distance"
        );
    }

    @Test
    void parsesAWildcard() {
        Assertions.assertEquals(
            QueryOp.WILDCARD, QueryParserTest.parse("hello*").type(),
            "Wildcard query"
        );
    }

    @Test
    void parsesAField() {
        Assertions.assertEquals(
            QueryOp.LEAF_TERM, QueryParserTest.parse("title:hello").type(),
            "Field value as a term"
        );
    }

    @Test
    void parsesAValueRange() {
        Assertions.assertEquals(
            QueryOp.VALUE_RANGE,
            QueryParserTest.parse("0:[a TO z]").type(),
            "Value range over a slot"
        );
    }

    @Test
    void parsesParentheses() {
        final Query query = QueryParserTest.parse("(hello OR world) AND test");
        Assertions.assertEquals(
            List.of(QueryOp.AND, QueryOp.OR),
            List.of(query.type(), query.subquery(0).type()),
            "Union inside a conjunction"
        );
    }

    @Test
    void parsesALeadingPlus() {
        Assertions.assertEquals(
            QueryOp.AND, QueryParserTest.parse("+hello world").type(),
            "Required term"
        );
    }

    @Test
    void parsesALeadingMinus() {
        Assertions.assertEquals(
            QueryOp.AND_NOT, QueryParserTest.parse("-hello").type(),
            "Excluded term"
        );
    }

    @Test
    void parsesFieldsInsideGroups() {
        final Query query = QueryParserTest.parse(
            "title:hello AND (body:world OR abstract:test)"
        );
        Assertions.assertEquals(
            List.of(QueryOp.AND, 2),
            List.of(query.type(), query.subqueryCount()),
            "Conjunction of a field and a group"
        );
    }

    @Test
    void parsesAnEmptyQuery() {
        Assertions.assertEquals(
            Query.MATCH_NOTHING, QueryParserTest.parse(""),
            "No terms"
        );
    }

    @Test
    void parsesAWhitespaceQuery() {
        Assertions.assertEquals(
            Query.MATCH_NOTHING, QueryParserTest.parse(String.format(" \t%n ")),
            "No terms"
        );
    }

    @Test
    void appliesUnionBelowConjunction() {
        final Query query = QueryParserTest.parse("a OR b AND c");
        Assertions.assertEquals(
            List.of(QueryOp.OR, QueryOp.AND),
            List.of(query.type(), query.subquery(1).type()),
            "Conjunction groups tighter than union"
        );
    }

    @Test
    void keepsAConjunctionFlat() {
        final Query query = QueryParserTest.parse("a b c");
        Assertions.assertEquals(
            List.of(QueryOp.AND, 3),
            List.of(query.type(), query.subqueryCount()),
            "Three conjuncts in one node"
        );
    }

    @Test
    void rejectsATrailingOperator() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> QueryParserTest.parse("a OR )"),
            "Missing operand"
        );
    }

    @Test
    void treatsLowerCaseKeywordsAsTerms() {
        Assertions.assertEquals(
            "Query((android AND candy AND orange))",
            QueryParserTest.parse("android candy orange").description(),
            "Lower case words stay terms"
        );
    }

    @Test
    void appliesADifferenceToTheAccumulatedConjunction() {
        final Query query = QueryParserTest.parse("a b NOT c");
        Assertions.assertEquals(
            List.of(QueryOp.AND_NOT, QueryOp.AND),
            List.of(query.type(), query.subquery(0).type()),
            "Difference of a conjunction"
        );
    }

    @Test
    void usesTheSelectedDefaultOperator() {
        final Query query = QueryParserTest.parse("a b", QueryOp.OR);
        Assertions.assertEquals(
            QueryOp.OR, query.type(), "Default operator of the parser"
        );
    }

    @Test
    void rejectsAnUnexpectedEnd() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> QueryParserTest.parse("a AND"),
            "Missing operand"
        );
    }

    @Test
    void rejectsAnUnclosedGroup() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> QueryParserTest.parse("(a OR b"),
            "Missing closing parenthesis"
        );
    }

    private static Query parse(final String text) {
        return new QueryParser(
            new QueryParserTokeniser(text).tokenise(), QueryOp.AND
        ).parse();
    }

    private static Query parse(final String text, final QueryOp defop) {
        return new QueryParser(
            new QueryParserTokeniser(text).tokenise(), defop
        ).parse();
    }
}
