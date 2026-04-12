/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query parser.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.QueryParserError;
import io.github.andreoss.search4j.core.stem.Stem;
import io.github.andreoss.search4j.core.termgen.Stopper;
import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.StemStrategy;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link QueryParser} settings; descriptions come from the
 * reference command-line parser.
 *
 * @since 0.1.0
 */
final class QueryParserTest {

    @Test
    void joinsWordsByDisjunctionByDefault() {
        Assertions.assertEquals(
            "Query((hello@1 OR world@2))",
            new QueryParser().parse("hello world").description(),
            "default operator"
        );
    }

    @Test
    void joinsWordsByTheSelectedOperator() {
        final QueryParser parser = new QueryParser();
        parser.defaultOp(QueryOp.AND);
        Assertions.assertEquals(
            "Query((x@1 AND y@2 AND z@3 AND w@4))",
            parser.parse("x y z AND w").description(),
            "selected operator"
        );
    }

    @Test
    void reportsTheDefaultOperator() {
        final QueryParser parser = new QueryParser();
        parser.defaultOp(QueryOp.NEAR);
        Assertions.assertEquals(QueryOp.NEAR, parser.defaultOp(), "operator");
    }

    @Test
    void rejectsAnOperatorThatCannotJoinWords() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new QueryParser().defaultOp(QueryOp.XOR),
            "XOR as default"
        );
    }

    @Test
    void stemsLowerCaseWords() {
        final QueryParser parser = new QueryParser();
        parser.stemmer(Stem.of("porter"), StemStrategy.SOME);
        Assertions.assertEquals(
            "Query((Zrun@1 OR Zquickli@2))",
            parser.parse("running quickly").description(),
            "stemmed words"
        );
    }

    @Test
    void dropsStopWordsFromGroups() {
        final QueryParser parser = new QueryParser();
        parser.stopper(Stopper.of("the"));
        Assertions.assertEquals(
            "Query((hello@1 OR world@3))",
            parser.parse("hello the world").description(),
            "stopped word"
        );
    }

    @Test
    void reportsAMissingOperand() {
        Assertions.assertEquals(
            "Syntax: <expression> AND <expression>",
            Assertions.assertThrows(
                QueryParserError.class,
                () -> new QueryParser().parse("a AND"),
                "missing operand"
            ).getMessage(),
            "message"
        );
    }

    @Test
    void parsesAgainWithoutSyntaxAfterAnError() {
        Assertions.assertEquals(
            "Query((x@1 OR near@2 OR y@3 OR z@4))",
            new QueryParser().parse("x NEAR y z").description(),
            "second parse"
        );
    }

    @Test
    void parsesAnEmptyQuery() {
        Assertions.assertEquals(
            "Query()",
            new QueryParser().parse("", Set.of(ParseFlag.BOOLEAN)).description(),
            "empty query"
        );
    }
}
