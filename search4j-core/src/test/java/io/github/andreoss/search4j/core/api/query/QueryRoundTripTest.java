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
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Round trips of query trees through the wire format: every operator over
 * every kind of leaf, and seeded random trees.
 *
 * @since 0.1.0
 */
final class QueryRoundTripTest {

    /**
     * Operators accepting any subquery.
     */
    private static final List<QueryOp> FREE = List.of(
        QueryOp.AND, QueryOp.OR, QueryOp.AND_NOT, QueryOp.XOR, QueryOp.AND_MAYBE,
        QueryOp.FILTER, QueryOp.ELITE_SET, QueryOp.SYNONYM, QueryOp.MAX
    );

    @ParameterizedTest
    @MethodSource("pairs")
    void roundTripsEveryOperatorOverEveryLeaf(final Query query) {
        Assertions.assertEquals(
            query, Query.deserialise(query.serialise()), query.description()
        );
    }

    @ParameterizedTest
    @MethodSource("random")
    void roundTripsRandomTrees(final Query query) {
        final Query back = Query.deserialise(query.serialise());
        Assertions.assertEquals(
            List.of(query, query.description()), List.of(back, back.description()),
            query.description()
        );
    }

    @Test
    void roundTripsEveryLeaf() {
        final List<Query> back = new ArrayList<>(QueryRoundTripTest.leaves().size());
        for (final Query leaf : QueryRoundTripTest.leaves()) {
            back.add(Query.deserialise(leaf.serialise()));
        }
        Assertions.assertEquals(QueryRoundTripTest.leaves(), back, "Leaves");
    }

    @Test
    void roundTripsPositionalOperators() {
        final List<Query> queries = List.of(
            Query.terms(QueryOp.PHRASE, List.of("a", "b")),
            Query.terms(QueryOp.NEAR, List.of("a", "b", "c"), 7),
            Query.of(
                QueryOp.PHRASE, List.of(Query.term("a"), Query.term("b").or(Query.term("c")))
            ),
            Query.of(
                QueryOp.NEAR,
                List.of(
                    Query.term("a", new TermCount(2), new TermPos(1)),
                    Query.term("b", new TermCount(1), new TermPos(2))
                ),
                3
            )
        );
        final List<Query> back = new ArrayList<>(queries.size());
        for (final Query query : queries) {
            back.add(Query.deserialise(query.serialise()));
        }
        Assertions.assertEquals(queries, back, "Positional operators");
    }

    private static Stream<Query> pairs() {
        final List<Query> leaves = QueryRoundTripTest.leaves();
        final List<Query> result = new ArrayList<>(128);
        for (final QueryOp type : QueryRoundTripTest.FREE) {
            for (int index = 0; index < leaves.size(); ++index) {
                result.add(
                    Query.of(
                        type, leaves.get(index), leaves.get((index + 1) % leaves.size())
                    )
                );
            }
        }
        return result.stream();
    }

    private static Stream<Query> random() {
        final Random random = new Random(21L);
        final List<Query> result = new ArrayList<>(150);
        while (result.size() < 150) {
            final Query tree = QueryRoundTripTest.tree(random, 3);
            if (!tree.empty()) {
                result.add(tree);
            }
        }
        return result.stream();
    }

    private static Query tree(final Random random, final int depth) {
        final Query result;
        final int pick = random.nextInt(10);
        if (depth == 0 || pick < 3) {
            final List<Query> leaves = QueryRoundTripTest.leaves();
            result = leaves.get(random.nextInt(leaves.size()));
        } else if (pick == 3) {
            result = QueryRoundTripTest.scaled(random, depth);
        } else if (pick == 4) {
            result = QueryRoundTripTest.phrase(random);
        } else {
            result = QueryRoundTripTest.branch(random, depth);
        }
        return result;
    }

    private static Query scaled(final Random random, final int depth) {
        final Query sub = QueryRoundTripTest.tree(random, depth - 1);
        final Query result;
        if (sub.type() == QueryOp.SCALE_WEIGHT) {
            result = sub;
        } else {
            result = Query.scale(random.nextInt(8) * 0.25, sub);
        }
        return result;
    }

    private static Query phrase(final Random random) {
        final List<String> words = new ArrayList<>(4);
        final int count = 2 + random.nextInt(9);
        for (int index = 0; index < count; ++index) {
            words.add(String.format("w%d", random.nextInt(40)));
        }
        return Query.terms(QueryOp.PHRASE, words, random.nextInt(3) * count);
    }

    private static Query branch(final Random random, final int depth) {
        final List<Query> subs = new ArrayList<>(4);
        final int count = 1 + random.nextInt(10);
        for (int index = 0; index < count; ++index) {
            subs.add(QueryRoundTripTest.tree(random, depth - 1));
        }
        final QueryOp type = QueryRoundTripTest.FREE.get(
            random.nextInt(QueryRoundTripTest.FREE.size())
        );
        int parameter = 0;
        if (type == QueryOp.ELITE_SET) {
            parameter = random.nextInt(4);
        }
        return Query.of(type, subs, parameter);
    }

    private static List<Query> leaves() {
        return List.of(
            Query.MATCH_ALL, Query.MATCH_NOTHING, Query.term("t"),
            Query.term("long term past sixteen bytes", new TermCount(3), new TermPos(9)),
            Query.term("bool", TermCount.ZERO, TermPos.FIRST),
            Query.term("", new TermCount(4), new TermPos(2)),
            Query.term("über中", new TermCount(1), new TermPos(1000)),
            Query.valueRange(new ValueNo(0L), "a", "z"),
            Query.value(QueryOp.VALUE_GE, new ValueNo(99L), "m"),
            Query.value(QueryOp.VALUE_LE, new ValueNo(14L), "n"),
            Query.wildcard("pre"),
            Query.wildcard(
                new Wildcard(
                    "p*t?", new Expansion(new TermCount(7), WildcardLimit.MOST_FREQUENT),
                    WildcardPattern.GLOB, QueryOp.MAX
                )
            ),
            Query.editDistance(
                EditDistance.of("target").withEdits(1).withPrefix(2).withCombiner(QueryOp.OR)
            ),
            Query.invalid(),
            Query.scale(1.5, Query.term("s")),
            Query.scale(QueryOp.SCALE_WEIGHT, Query.term("x").or(Query.term("y")), 1e-300)
        );
    }
}
