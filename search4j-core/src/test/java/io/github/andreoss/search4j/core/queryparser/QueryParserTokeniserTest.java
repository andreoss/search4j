/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for QueryParserTokeniser.
 */
package io.github.andreoss.search4j.core.queryparser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryParserTokeniser.
 */
class QueryParserTokeniserTest {

    @Test
    void tokeniseSimpleTerm() {
        final List<QueryParserToken> tokens = tokenise("hello");
        assertEquals(2, tokens.size()); // term + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(0)).text());
    }

    @Test
    void tokeniseMultipleTerms() {
        final List<QueryParserToken> tokens = tokenise("hello world");
        assertEquals(3, tokens.size()); // term + term + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(0)).text());
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertEquals("world", ((QueryParserToken.Term) tokens.get(1)).text());
    }

    @Test
    void tokenisePhrase() {
        final List<QueryParserToken> tokens = tokenise("\"hello world\"");
        assertEquals(2, tokens.size()); // phrase + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Phrase);
        final String[] terms = ((QueryParserToken.Phrase) tokens.get(0)).terms();
        assertArrayEquals(new String[]{"hello", "world"}, terms);
    }

    @Test
    void tokeniseAndOperator() {
        final List<QueryParserToken> tokens = tokenise("hello AND world");
        // term + operator + term + EOF
        assertEquals(4, tokens.size());
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(2) instanceof QueryParserToken.Term);
    }

    @Test
    void tokeniseOrOperator() {
        final List<QueryParserToken> tokens = tokenise("hello OR world");
        assertEquals(4, tokens.size());
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(2) instanceof QueryParserToken.Term);
    }

    @Test
    void tokeniseNotOperator() {
        final List<QueryParserToken> tokens = tokenise("hello NOT world");
        assertEquals(4, tokens.size());
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(2) instanceof QueryParserToken.Term);
    }

    @Test
    void tokenisePlusOperator() {
        final List<QueryParserToken> tokens = tokenise("+hello");
        assertEquals(3, tokens.size()); // operator + term + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(1)).text());
    }

    @Test
    void tokeniseMinusOperator() {
        final List<QueryParserToken> tokens = tokenise("-hello");
        assertEquals(3, tokens.size()); // operator + term + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(1)).text());
    }

    @Test
    void tokeniseWildcard() {
        final List<QueryParserToken> tokens = tokenise("hello*");
        assertEquals(2, tokens.size()); // wildcard + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Wildcard);
        assertEquals("hello*", ((QueryParserToken.Wildcard) tokens.get(0)).pattern());
    }

    @Test
    void tokeniseQuestionWildcard() {
        final List<QueryParserToken> tokens = tokenise("hell?");
        assertEquals(2, tokens.size()); // wildcard + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Wildcard);
        assertEquals("hell?", ((QueryParserToken.Wildcard) tokens.get(0)).pattern());
    }

    @Test
    void tokeniseRange() {
        final List<QueryParserToken> tokens = tokenise("[a TO z]");
        assertEquals(2, tokens.size()); // range + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Range);
        final QueryParserToken.Range range = (QueryParserToken.Range) tokens.get(0);
        assertEquals("a", range.lower());
        assertEquals("z", range.upper());
        assertTrue(range.inclusive());
    }

    @Test
    void tokeniseFieldPrefix() {
        final List<QueryParserToken> tokens = tokenise("title:hello");
        assertEquals(3, tokens.size()); // prefix + term + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Prefix);
        assertEquals("title", ((QueryParserToken.Prefix) tokens.get(0)).field());
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(1)).text());
    }

    @Test
    void tokeniseParentheses() {
        final List<QueryParserToken> tokens = tokenise("(hello world)");
        assertEquals(5, tokens.size()); // group start + term + term + group end + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.GroupStart);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(2) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(3) instanceof QueryParserToken.GroupEnd);
    }

    @Test
    void tokeniseComplex() {
        final List<QueryParserToken> tokens = tokenise("title:hello AND body:world");
        // prefix + term + operator + prefix + term + EOF = 6
        assertEquals(6, tokens.size());
        assertTrue(tokens.get(0) instanceof QueryParserToken.Prefix);
        assertTrue(tokens.get(1) instanceof QueryParserToken.Term);
        assertTrue(tokens.get(2) instanceof QueryParserToken.Operator);
        assertTrue(tokens.get(3) instanceof QueryParserToken.Prefix);
        assertTrue(tokens.get(4) instanceof QueryParserToken.Term);
    }

    @Test
    void tokeniseTilde() {
        final List<QueryParserToken> tokens = tokenise("hello~");
        assertEquals(3, tokens.size()); // term + operator + EOF
        assertTrue(tokens.get(0) instanceof QueryParserToken.Term);
        assertEquals("hello", ((QueryParserToken.Term) tokens.get(0)).text());
        assertTrue(tokens.get(1) instanceof QueryParserToken.Operator);
    }

    @Test
    void tokeniseEmpty() {
        final List<QueryParserToken> tokens = tokenise("");
        assertEquals(1, tokens.size()); // EOF only
        assertEquals(QueryParserToken.EOF.INSTANCE, tokens.get(0));
    }

    @Test
    void tokeniseWhitespaceOnly() {
        final List<QueryParserToken> tokens = tokenise("   \t\n  ");
        assertEquals(1, tokens.size()); // EOF only
        assertEquals(QueryParserToken.EOF.INSTANCE, tokens.get(0));
    }

    private List<QueryParserToken> tokenise(final String query) {
        return new QueryParserTokeniser(query).tokenise();
    }
}
