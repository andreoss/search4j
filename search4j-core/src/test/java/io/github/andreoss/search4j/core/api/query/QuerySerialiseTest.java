/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Byte vectors of the query wire format, derived by hand from the reference
 * encoder: the first byte of each node, the packed integers and strings, the
 * little-endian scale factors.
 *
 * @since 0.1.0
 */
final class QuerySerialiseTest {

    /**
     * Sixteen letters, the first length not held in the type byte.
     */
    private static final String SIXTEEN = "abcdefghijklmnop";

    @ParameterizedTest(name = "{0}")
    @MethodSource({"leaves", "values", "expansions", "branches"})
    void writesReferenceBytes(final String name, final Query query,
        final String hex) {
        Assertions.assertEquals(
            hex, HexFormat.of().formatHex(query.serialise()), name
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource({"leaves", "values", "expansions", "branches"})
    void readsReferenceBytes(final String name, final Query query,
        final String hex) {
        Assertions.assertEquals(
            query, Query.deserialise(HexFormat.of().parseHex(hex)), name
        );
    }

    @Test
    void writesTheReferencePhraseTest() {
        Assertions.assertEquals(
            String.join(
                "", "0d1f85eb51b81e0940", "8a53303037", "fb03",
                "56", "736861", "6b656e", "536e6f74", "57", "73746972726564"
            ),
            HexFormat.of().formatHex(
                Query.scale(
                    QueryOp.SCALE_WEIGHT,
                    Query.term("007").or(
                        Query.terms(QueryOp.PHRASE, List.of("shaken", "not", "stirred"))
                    ),
                    3.14
                ).serialise()
            ),
            "Reference serialise_query1 phrase under a scale"
        );
    }

    @Test
    void roundTripsTheReferenceQueries() {
        final List<Query> queries = List.of(
            Query.MATCH_NOTHING, Query.term("hello"),
            Query.term("hello", new TermCount(1), new TermPos(1)),
            Query.term("hello").or(Query.term("world")),
            Query.of(
                QueryOp.OR, Query.term("hello", new TermCount(1), new TermPos(1)),
                Query.term("world", new TermCount(1), new TermPos(1))
            )
        );
        final List<String> expected = List.of(
            "Query()", "Query(hello)", "Query(hello@1)",
            "Query((hello OR world))", "Query((hello@1 OR world@1))"
        );
        final List<String> actual = new ArrayList<>(queries.size());
        for (final Query query : queries) {
            actual.add(Query.deserialise(query.serialise()).description());
        }
        Assertions.assertEquals(expected, actual, "Reference serialise_query1");
    }

    @Test
    void readsBranchesWithoutConstructionChecks() {
        Assertions.assertEquals(
            "Query(((a AND b) PHRASE 2 c))",
            Query.deserialise(
                HexFormat.of().parseHex("fa0282516151625163")
            ).description(),
            "A phrase over a conjunction is read as written"
        );
    }

    @Test
    void readsLeavesWithoutConstructionSimplifications() {
        Assertions.assertEquals(
            List.of(
                "Query(VALUE_GE 3 )", "Query(VALUE_RANGE 1 b a)",
                "Query(WILDCARD SYNONYM )", "Query(VALUE_LE 1 a)"
            ),
            List.of(
                Query.deserialise(HexFormat.of().parseHex("3300")).description(),
                Query.deserialise(HexFormat.of().parseHex("2101620161"))
                    .description(),
                Query.deserialise(HexFormat.of().parseHex("0b00000d00"))
                    .description(),
                Query.deserialise(HexFormat.of().parseHex("21000161"))
                    .description()
            ),
            "Value and wildcard leaves are built directly"
        );
    }

    @Test
    void defaultsWindowsAndSetSizes() {
        Assertions.assertEquals(
            List.of("Query((a PHRASE 2 b))", "Query((a ELITE_SET 10 b))"),
            List.of(
                Query.deserialise(HexFormat.of().parseHex("fa0051615162"))
                    .description(),
                Query.deserialise(HexFormat.of().parseHex("ea0051615162"))
                    .description()
            ),
            "A zero window or set size takes the default"
        );
    }

    @Test
    void appliesTheSimplificationsOfTheBranches() {
        Assertions.assertEquals(
            List.of(
                "Query(a)", "Query()", "Query(a)", "Query()", "Query()", "Query((SYNONYM (a AND b)))", "Query(WILDCARD SYNONYM a)"
            ),
            List.of(
                Query.deserialise(HexFormat.of().parseHex("815161")).description(),
                Query.deserialise(HexFormat.of().parseHex("825161")).description(),
                Query.deserialise(HexFormat.of().parseHex("8a5161")).description(),
                Query.deserialise(HexFormat.of().parseHex("92")).description(),
                Query.deserialise(HexFormat.of().parseHex("88ffffffff0f"))
                    .description(),
                Query.deserialise(HexFormat.of().parseHex("b18251615162"))
                    .description(),
                Query.deserialise(HexFormat.of().parseHex("b10b0000010161"))
                    .description()
            ),
            "Single, missing and scaled subqueries as the reference adds them"
        );
    }

    @Test
    void stripsScalingOnTheRightOfADifference() {
        Assertions.assertEquals(
            "Query((a AND_NOT b AND_NOT c))",
            Query.deserialise(
                HexFormat.of().parseHex("93516151620d00000000000000405163")
            ).description(),
            "The scale on the right of a difference is dropped"
        );
    }

    @Test
    void stripsOneScaleMoreWhenReadingADifference() {
        final Query query = Query.term("a").andNot(
            Query.scale(2.0, Query.scale(3.0, Query.term("b")))
        );
        Assertions.assertEquals(
            List.of("Query((a AND_NOT 3 * b))", "Query((a AND_NOT b))"),
            List.of(
                query.description(),
                Query.deserialise(query.serialise()).description()
            ),
            "Reading adds the right side again, which strips the next scale"
        );
    }

    @Test
    void dropsPatternBitsOfAnEditDistance() {
        Assertions.assertEquals(
            "0a00000d02000161",
            HexFormat.of().formatHex(
                Query.deserialise(
                    HexFormat.of().parseHex("0a00300d02000161")
                ).serialise()
            ),
            "Pattern bits of an edit distance are written as zero"
        );
    }

    @Test
    void readsTheLargestCountOfSubqueries() {
        Assertions.assertTrue(
            Query.deserialise(
                HexFormat.of().parseHex("88ffffffffffffffffff00")
            ).empty(),
            "A count of ten bytes with a clear top byte is read"
        );
    }

    @Test
    void writesTheEmptyArrayForMatchNothing() {
        Assertions.assertEquals(
            0, Query.MATCH_NOTHING.serialise().length, "Match-nothing is empty"
        );
    }

    @Test
    void readsTheEmptyArrayAsMatchNothing() {
        Assertions.assertTrue(
            Query.deserialise(new byte[0]).empty(), "Empty bytes match nothing"
        );
    }

    @Test
    void roundTripsSpecialFactors() {
        Assertions.assertEquals(
            List.of("Query(nan * a)", "Query(inf * a)", "Query(0 * a)"),
            List.of(
                Query.deserialise(
                    Query.scale(Double.NaN, Query.term("a")).serialise()
                ).description(),
                Query.deserialise(
                    Query.scale(Double.POSITIVE_INFINITY, Query.term("a")).serialise()
                ).description(),
                Query.deserialise(Query.scale(0.0, Query.term("a")).serialise())
                    .description()
            ),
            "Special factors survive"
        );
    }

    private static Stream<Arguments> leaves() {
        return Stream.of(
            Arguments.of("match all", Query.MATCH_ALL, "0f"),
            Arguments.of(
                "mutant match all",
                Query.term("", new TermCount(2), new TermPos(3)), "0e0203"
            ),
            Arguments.of(
                "match all at a position",
                Query.term("", new TermCount(1), new TermPos(1)), "0e0101"
            ),
            Arguments.of("free text term", Query.term("hello"), "5568656c6c6f"),
            Arguments.of(
                "positioned term",
                Query.term("hello", new TermCount(1), new TermPos(1)),
                "6568656c6c6f01"
            ),
            Arguments.of(
                "weighted term",
                Query.term("hello", new TermCount(2), TermPos.FIRST),
                "7568656c6c6f0200"
            ),
            Arguments.of(
                "boolean term",
                Query.term("hello", TermCount.ZERO, TermPos.FIRST),
                "4568656c6c6f"
            ),
            Arguments.of(
                "boolean term at a position",
                Query.term("hello", TermCount.ZERO, new TermPos(7)),
                "7568656c6c6f0007"
            ),
            Arguments.of(
                "fifteen bytes", Query.term("abcdefghijklmno"),
                "5f6162636465666768696a6b6c6d6e6f"
            ),
            Arguments.of(
                "sixteen bytes", Query.term(QuerySerialiseTest.SIXTEEN),
                "50006162636465666768696a6b6c6d6e6f70"
            ),
            Arguments.of(
                "long positioned term",
                Query.term("abcdefghijklmnopqrst", new TermCount(1), new TermPos(300)),
                "60046162636465666768696a6b6c6d6e6f7071727374ac02"
            ),
            Arguments.of(
                "long weighted term",
                Query.term(QuerySerialiseTest.SIXTEEN.concat("q"), new TermCount(3), TermPos.FIRST),
                "70016162636465666768696a6b6c6d6e6f70710300"
            ),
            Arguments.of(
                "long boolean term",
                Query.term(QuerySerialiseTest.SIXTEEN, TermCount.ZERO, TermPos.FIRST),
                "40006162636465666768696a6b6c6d6e6f70"
            ),
            Arguments.of("multibyte term", Query.term("é"), "52c3a9"),
            Arguments.of("invalid", Query.invalid(), "00"),
            Arguments.of(
                "scale", Query.scale(2.0, Query.term("hello")),
                "0d00000000000000405568656c6c6f"
            ),
            Arguments.of(
                "fractional scale", Query.scale(0.5, Query.term("a")),
                "0d000000000000e03f5161"
            )
        );
    }

    private static Stream<Arguments> values() {
        return Stream.of(
            Arguments.of(
                "range", Query.valueRange(new ValueNo(1L), "a", "b"), "2101610162"
            ),
            Arguments.of(
                "range in slot 15", Query.valueRange(new ValueNo(15L), "a", "b"),
                "2f0001610162"
            ),
            Arguments.of(
                "range in slot 300", Query.valueRange(new ValueNo(300L), "a", "b"),
                "2f9d0201610162"
            ),
            Arguments.of(
                "range in slot 14", Query.value(QueryOp.VALUE_LE, new ValueNo(14L), "n"),
                "2e00016e"
            ),
            Arguments.of(
                "upper bound", Query.value(QueryOp.VALUE_LE, new ValueNo(2L), "z"),
                "2200017a"
            ),
            Arguments.of(
                "lower bound", Query.value(QueryOp.VALUE_GE, new ValueNo(3L), "m"),
                "33016d"
            ),
            Arguments.of(
                "lower bound in slot 20",
                Query.value(QueryOp.VALUE_GE, new ValueNo(20L), "m"), "3f05016d"
            ),
            Arguments.of(
                "upper bound in the largest slot",
                Query.value(QueryOp.VALUE_LE, new ValueNo(ValueNo.BAD), "z"),
                "2ff0ffffff0f00017a"
            )
        );
    }

    private static Stream<Arguments> expansions() {
        return Stream.of(
            Arguments.of("wildcard", Query.wildcard("abc"), "0b00000d03616263"),
            Arguments.of(
                "limited glob",
                Query.wildcard(
                    new Wildcard(
                        "a*c", new Expansion(new TermCount(5), WildcardLimit.FIRST),
                        WildcardPattern.GLOB, QueryOp.OR
                    )
                ),
                "0b05310103612a63"
            ),
            Arguments.of(
                "most frequent single",
                Query.wildcard(
                    new Wildcard(
                        "a?", new Expansion(new TermCount(200), WildcardLimit.MOST_FREQUENT),
                        WildcardPattern.SINGLE, QueryOp.MAX
                    )
                ),
                "0bc801220e02613f"
            ),
            Arguments.of(
                "multi", Query.wildcard(Wildcard.of("a*").withGlob(WildcardPattern.MULTI)),
                "0b00100d02612a"
            ),
            Arguments.of(
                "edit distance", Query.editDistance("abc"), "0a00000d020003616263"
            ),
            Arguments.of(
                "limited edit distance",
                Query.editDistance(
                    new EditDistance(
                        "abc", new Expansion(new TermCount(10), WildcardLimit.MOST_FREQUENT),
                        QueryOp.MAX, 1, 2
                    )
                ),
                "0a0a020e010203616263"
            ),
            Arguments.of(
                "first edit distance",
                Query.editDistance(
                    EditDistance.of("ab").withExpansion(
                        new Expansion(new TermCount(3), WildcardLimit.FIRST)
                    ).withCombiner(QueryOp.OR).withEdits(130)
                ),
                "0a030101820100026162"
            )
        );
    }

    private static Stream<Arguments> branches() {
        final Query alpha = Query.term("a");
        final Query beta = Query.term("b");
        return Stream.of(
            Arguments.of("and", alpha.and(beta), "8251615162"),
            Arguments.of("or", alpha.or(beta), "8a51615162"),
            Arguments.of("and not", alpha.andNot(beta), "9251615162"),
            Arguments.of("xor", alpha.xor(beta), "9a51615162"),
            Arguments.of(
                "and maybe", Query.of(QueryOp.AND_MAYBE, alpha, beta), "a251615162"
            ),
            Arguments.of(
                "filter", Query.of(QueryOp.FILTER, alpha, beta), "aa51615162"
            ),
            Arguments.of(
                "synonym", Query.of(QueryOp.SYNONYM, alpha, beta), "b251615162"
            ),
            Arguments.of("max", Query.of(QueryOp.MAX, alpha, beta), "ba51615162"),
            Arguments.of(
                "elite set", Query.of(QueryOp.ELITE_SET, alpha, beta), "ea0a51615162"
            ),
            Arguments.of(
                "near", Query.of(QueryOp.NEAR, alpha, beta), "f20251615162"
            ),
            Arguments.of(
                "phrase", Query.terms(QueryOp.PHRASE, List.of("a", "b", "c"), 5),
                "fb05516151625163"
            ),
            Arguments.of(
                "single synonym", Query.of(QueryOp.SYNONYM, List.of(alpha.and(beta))),
                "b18251615162"
            ),
            Arguments.of(
                "seven subqueries",
                Query.terms(QueryOp.OR, List.of("a", "b", "c", "d", "e", "f", "g")),
                "8f5161516251635164516551665167"
            ),
            Arguments.of(
                "eight subqueries",
                Query.terms(QueryOp.OR, List.of("a", "b", "c", "d", "e", "f", "g", "h")),
                "880051615162516351645165516651675168"
            ),
            Arguments.of(
                "ten subqueries",
                Query.terms(
                    QueryOp.AND, List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
                ),
                "8002516151625163516451655166516751685169516a"
            ),
            Arguments.of(
                "nine phrase terms",
                Query.terms(
                    QueryOp.PHRASE, List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), 12
                ),
                "f8010c516151625163516451655166516751685169"
            ),
            Arguments.of(
                "nested",
                Query.scale(2.0, alpha.and(beta)).or(Query.term("c").not()),
                "8a0d00000000000000408251615162920f5163"
            )
        );
    }
}
