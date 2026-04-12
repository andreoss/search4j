/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.queryparser.QueryParser;
import io.github.andreoss.search4j.core.queryparser.QueryParserTokeniser;
import io.github.andreoss.search4j.core.types.QueryOp;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Compares parser output against the reference parser over a corpus of
 * queries covering plain terms, boolean operators, phrases, positional
 * windows, wildcards, value ranges, stemming, synonyms and parser flags.
 *
 * @since 0.1.0
 */
final class QueryParserDifferentialTest {

    /**
     * Collapses whitespace runs in a query description.
     */
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * Plain single-term queries.
     */
    private static final String[] PLAIN = {
        "hello",
        "world",
        "test",
        "document",
        "universe",
        "goodbye",
    };

    /**
     * Boolean operator queries.
     */
    private static final String[] BOOLEAN = {
        "hello AND world",
        "hello OR world",
        "hello AND NOT world",
        "hello XOR world",
        "(hello AND world) OR test",
        "hello AND (world OR universe)",
    };

    /**
     * Quoted phrase queries.
     */
    private static final String[] PHRASE = {
        "\"hello world\"",
        "\"hello universe\"",
        "\"goodbye world\"",
        "\"hello world test\"",
    };

    /**
     * Positional window queries, explicit and default.
     */
    private static final String[] WINDOWS = {
        "hello NEAR/3 world",
        "hello ADJ/3 world",
        "hello NEAR world",
        "hello ADJ world",
        "a NEAR/3 b",
        "a ADJ/1 b",
    };

    /**
     * Wildcard queries.
     */
    private static final String[] WILDCARDS = {
        "hello*",
        "hel*o",
        "*world",
        "te*t",
        "doc*",
    };

    /**
     * Value range queries.
     */
    private static final String[] RANGES = {
        "VALUE:0:title1..title3",
        "VALUE:1:category1..category2",
        "VALUE:0:title1..title1",
    };

    /**
     * Queries exercised with stemming enabled.
     */
    private static final String[] STEMMED = {
        "testing",
        "tested",
        "tests",
        "documents",
    };

    /**
     * Synonym operator queries.
     */
    private static final String[] SYNONYMS = {
        "hello SYNONYM world",
        "test SYNONYM document",
    };

    /**
     * Parser flag names exercised on one query.
     */
    private static final String[] FLAGS = {
        "default",
        "boolean",
        "lovehate",
        "partial",
        "synonym",
        "flag_auto_multiword_synonym",
        "flag_cjk_ngram",
    };

    @Test
    void parsesPlainTerms() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.PLAIN) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void parsesBooleanOperators() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.BOOLEAN) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void parsesPhrases() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.PHRASE) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void parsesPositionalWindows() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.WINDOWS) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void parsesWildcards() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.WILDCARDS) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void parsesValueRanges() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.RANGES) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void stemsTerms() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.STEMMED) {
            this.assertSame(que, "default", "english");
        }
    }

    @Test
    void expandsSynonyms() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String que : QueryParserDifferentialTest.SYNONYMS) {
            this.assertSame(que, "default", "none");
        }
    }

    @Test
    void honoursParserFlags() {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        for (final String flags : QueryParserDifferentialTest.FLAGS) {
            this.assertSame("hello world", flags, "none");
        }
    }

    private void assertSame(final String que, final String flags,
        final String stemmer) {
        Assertions.assertEquals(
            QueryParserDifferentialTest.normalise(
                this.parseNative(que, flags, stemmer)
            ),
            QueryParserDifferentialTest.normalise(this.parseProject(que)),
            String.format("query %s with %s and %s", que, flags, stemmer)
        );
    }

    private String parseProject(final String que) {
        return new QueryParser(
            new QueryParserTokeniser(que).tokenise(),
            QueryOp.AND
        ).parse().description();
    }

    private String parseNative(final String que, final String flags,
        final String stemmer) {
        try {
            return NativeTools.parse(que, flags, stemmer).get(0);
        } catch (final IOException exp) {
            throw new AssertionError(exp);
        }
    }

    private static String normalise(final String desc) {
        return QueryParserDifferentialTest.SPACES.matcher(desc)
            .replaceAll(" ").trim();
    }
}
