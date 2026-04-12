/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query parser tokeniser.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.QueryOp;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link QueryParserTokeniser}.
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.UnnecessaryLocalRule", "PMD.UnitTestContainsTooManyAsserts"})
final class QueryParserTokeniserTest {

    @Test
    void tokenisesASimpleTerm() {
        Assertions.assertEquals(
            2, QueryParserTokeniserTest.tokens("hello").size(),
            "Term and end of input"
        );
    }

    @Test
    void carriesTermText() {
        final QueryParserToken token = QueryParserTokeniserTest
            .tokens("hello").get(0);
        Assertions.assertEquals(
            "hello", ((QueryParserToken.Term) token).text(), "Term text"
        );
    }

    @Test
    void tokenisesTwoTerms() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("hello world");
        Assertions.assertEquals(
            List.of("hello", "world"),
            List.of(
                ((QueryParserToken.Term) found.get(0)).text(),
                ((QueryParserToken.Term) found.get(1)).text()
            ),
            "Terms in order"
        );
    }

    @Test
    void tokenisesAPhrase() {
        final QueryParserToken token = QueryParserTokeniserTest
            .tokens("\"hello world\"").get(0);
        Assertions.assertEquals(
            List.of("hello", "world"),
            ((QueryParserToken.Phrase) token).terms(),
            "Words of the phrase"
        );
    }

    @Test
    void escapesAQuoteInsideAPhrase() {
        final QueryParserToken token = QueryParserTokeniserTest
            .tokens("\"hello \\\"world\\\"\"").get(0);
        Assertions.assertEquals(
            List.of("hello", "\"world\""),
            ((QueryParserToken.Phrase) token).terms(),
            "Escaped quote"
        );
    }

    @Test
    void rejectsAnUnclosedPhrase() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> QueryParserTokeniserTest.tokens("\"hello"),
            "Unclosed phrase"
        );
    }

    @Test
    void tokenisesBooleanKeywords() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("hello AND world");
        Assertions.assertEquals(
            List.of(QueryOp.AND),
            List.of(((QueryParserToken.Operator) found.get(1)).op()),
            "Operator between the terms"
        );
    }

    @Test
    void tokenisesOrAndNotKeywords() {
        Assertions.assertEquals(
            List.of(QueryOp.OR, QueryOp.AND_NOT),
            List.of(
                ((QueryParserToken.Operator) QueryParserTokeniserTest
                    .tokens("hello OR world").get(1)).op(),
                ((QueryParserToken.Operator) QueryParserTokeniserTest
                    .tokens("hello NOT world").get(1)).op()
            ),
            "OR and NOT"
        );
    }

    @Test
    void treatsLowerCaseKeywordsAsTerms() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("hello and world");
        Assertions.assertEquals(
            4, found.size(), "Three terms and end of input"
        );
    }

    @Test
    void tokenisesThePlusOperator() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("+hello");
        Assertions.assertEquals(
            List.of(QueryOp.AND),
            List.of(((QueryParserToken.Operator) found.get(0)).op()),
            "Required term"
        );
    }

    @Test
    void tokenisesTheMinusOperator() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("-hello");
        Assertions.assertEquals(
            List.of(QueryOp.AND_NOT),
            List.of(((QueryParserToken.Operator) found.get(0)).op()),
            "Excluded term"
        );
    }

    @Test
    void tokenisesAWildcard() {
        final QueryParserToken token = QueryParserTokeniserTest
            .tokens("hello*").get(0);
        Assertions.assertEquals(
            "hello*", ((QueryParserToken.Wildcard) token).pattern(),
            "Pattern with its glob character"
        );
    }

    @Test
    void tokenisesASingleCharacterWildcard() {
        final QueryParserToken token = QueryParserTokeniserTest
            .tokens("hell?").get(0);
        Assertions.assertEquals(
            "hell?", ((QueryParserToken.Wildcard) token).pattern(),
            "Pattern with a question mark"
        );
    }

    @Test
    void tokenisesAnInclusiveRange() {
        final QueryParserToken.Range range =
            (QueryParserToken.Range) QueryParserTokeniserTest
                .tokens("[a TO z]").get(0);
        Assertions.assertEquals(
            List.of("a", "z"), List.of(range.lower(), range.upper()),
            "Bounds of the range"
        );
        Assertions.assertTrue(range.inclusive(), "Inclusive bounds");
    }

    @Test
    void tokenisesAnExclusiveRange() {
        final QueryParserToken.Range range =
            (QueryParserToken.Range) QueryParserTokeniserTest
                .tokens("{a TO z}").get(0);
        Assertions.assertFalse(range.inclusive(), "Exclusive bounds");
    }

    @Test
    void rejectsAMalformedRange() {
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> QueryParserTokeniserTest.tokens("[a z]"),
                "Missing separator"
            ),
            () -> Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> QueryParserTokeniserTest.tokens("[a TO z"),
                "Missing closing bracket"
            )
        );
    }

    @Test
    void tokenisesAField() {
        final QueryParserToken.FieldSpec spec =
            (QueryParserToken.FieldSpec) QueryParserTokeniserTest
                .tokens("title:hello").get(0);
        Assertions.assertEquals(
            List.of("title", "hello"), List.of(spec.field(), spec.value()),
            "Field name and value"
        );
    }

    @Test
    void tokenisesAFieldValueRange() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("0:[a TO z]");
        Assertions.assertEquals(
            List.of(QueryParserToken.Type.PREFIX, QueryParserToken.Type.RANGE),
            List.of(found.get(0).type(), found.get(1).type()),
            "Field name then range"
        );
    }

    @Test
    void tokenisesParentheses() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("(hello world)");
        Assertions.assertEquals(
            List.of(
                QueryParserToken.Type.GROUP_START,
                QueryParserToken.Type.TERM,
                QueryParserToken.Type.TERM,
                QueryParserToken.Type.GROUP_END
            ),
            List.of(
                found.get(0).type(), found.get(1).type(),
                found.get(2).type(), found.get(3).type()
            ),
            "Group around two terms"
        );
    }

    @Test
    void tokenisesProximityWithADistance() {
        final List<QueryParserToken> found =
            QueryParserTokeniserTest.tokens("hello ~5 world");
        Assertions.assertEquals(
            List.of(QueryOp.NEAR, 5),
            List.of(
                ((QueryParserToken.Operator) found.get(1)).op(),
                ((QueryParserToken.Operator) found.get(1)).parameter()
            ),
            "Near operator and its distance"
        );
    }

    @Test
    void tokenisesOnlyEndOfInputForAnEmptyQuery() {
        Assertions.assertEquals(
            List.of(QueryParserToken.End.INSTANCE),
            QueryParserTokeniserTest.tokens(""),
            "End of input"
        );
    }

    @Test
    void tokenisesOnlyEndOfInputForWhitespace() {
        Assertions.assertEquals(
            List.of(QueryParserToken.End.INSTANCE),
            QueryParserTokeniserTest.tokens(String.format(" \t%n ")),
            "End of input"
        );
    }

    private static List<QueryParserToken> tokens(final String query) {
        return new QueryParserTokeniser(query).tokenise();
    }
}
