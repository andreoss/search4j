/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Query trees resolved against a shard match the documents the operators
 * define: intersection, union, difference, odd parity, the left side of an
 * and-maybe, unchanged sets under scaling and value comparisons on the
 * stored strings; walked by stepping, skipping and checking.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class MatcherQueryTest {

    /**
     * Documents in the shard.
     */
    private static final int SIZE = 30;

    /**
     * Value slot of the documents.
     */
    private static final ValueNo SLOT = new ValueNo(1);

    /**
     * Divisors naming the terms.
     */
    private static final int[] DIVISORS = {2, 3, 5, 7};

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void matchesTheOperatorSets(final int seed) {
        final InMemoryShard shard = MatcherQueryTest.shard();
        final Random random = new Random(seed);
        for (int round = 0; round < 25; round = round + 1) {
            final MatcherQueryTest.Case tree = MatcherQueryTest.tree(random, 3);
            final List<Integer> expected = tree.docs().stream().boxed().toList();
            final String name = tree.query().description();
            Assertions.assertEquals(
                expected,
                MatcherQueryTest.stepped(Matcher.resolve(shard, tree.query())),
                name
            );
            final int stride = 1 + random.nextInt(4);
            Assertions.assertEquals(
                MatcherQueryTest.skipping(expected, stride),
                MatcherQueryTest.skipped(
                    Matcher.resolve(shard, tree.query()), stride
                ),
                name
            );
            MatcherQueryTest.checks(
                Matcher.resolve(shard, tree.query()), expected, name
            );
            if (tree.exact()) {
                MatcherQueryTest.bounds(
                    shard, tree.query(), expected.size(), name
                );
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(ints = {21, 22, 23, 24, 25, 26, 27, 28})
    void prunesOnlyDocumentsBelowTheMinimum(final int seed) {
        final InMemoryShard shard = MatcherQueryTest.shard();
        final Random random = new Random(seed);
        for (int round = 0; round < 25; round = round + 1) {
            final MatcherQueryTest.Case tree = MatcherQueryTest.tree(random, 3);
            if (!tree.exact()) {
                continue;
            }
            final Query query = tree.query();
            final List<Integer> all = new ArrayList<>(0);
            final List<Double> weights = new ArrayList<>(0);
            PostList list = Matcher.resolve(shard, query).next();
            while (!list.atEnd()) {
                all.add(list.docId().value());
                weights.add(list.weight(BooleanPostListFixture.doc()));
                list = list.next();
            }
            for (final double least : new double[] {0.5, 1.5, 2.5, 4.5}) {
                final List<Integer> heavy = IntStream.range(0, all.size())
                    .filter(idx -> weights.get(idx) >= least)
                    .mapToObj(all::get).toList();
                final String name = String.format(
                    "%s at least %s", query.description(), least
                );
                final List<Integer> stepped = MatcherQueryTest.pruned(
                    Matcher.resolve(shard, query), least, 0
                );
                Assertions.assertTrue(all.containsAll(stepped), name);
                Assertions.assertTrue(stepped.containsAll(heavy), name);
                final List<Integer> skipped = MatcherQueryTest.pruned(
                    Matcher.resolve(shard, query), least, 1
                );
                Assertions.assertTrue(all.containsAll(skipped), name);
                Assertions.assertTrue(skipped.containsAll(heavy), name);
            }
        }
    }

    @Test
    void matchesNothingForAnEmptyQuery() {
        Assertions.assertEquals(
            List.of(),
            MatcherQueryTest.stepped(
                Matcher.resolve(MatcherQueryTest.shard(), Query.MATCH_NOTHING)
            ),
            "empty query"
        );
    }

    @Test
    void matchesNothingForAMissingTerm() {
        Assertions.assertEquals(
            List.of(),
            MatcherQueryTest.stepped(
                Matcher.resolve(MatcherQueryTest.shard(), Query.term("absent"))
            ),
            "missing term"
        );
    }

    private static InMemoryShard shard() {
        final InMemoryShard shard = new InMemoryShard();
        for (int doc = 1; doc <= MatcherQueryTest.SIZE; doc = doc + 1) {
            final DocumentInternal document = new DocumentInternal();
            document.addTerm("any", new TermCount(1));
            for (final int divisor : MatcherQueryTest.DIVISORS) {
                if (doc % divisor == 0) {
                    document.addTerm(
                        String.format("t%d", divisor), new TermCount(doc % 4 + 1)
                    );
                }
            }
            if (doc % 4 != 0) {
                document.addValue(
                    MatcherQueryTest.SLOT, String.format("%02d", doc)
                );
            }
            shard.addDocument(document);
        }
        return shard;
    }

    private static MatcherQueryTest.Case tree(
        final Random random, final int depth
    ) {
        final MatcherQueryTest.Case result;
        if (depth == 0 || random.nextInt(3) == 0) {
            result = MatcherQueryTest.leaf(random);
        } else {
            result = MatcherQueryTest.branch(random, depth - 1);
        }
        return result;
    }

    private static MatcherQueryTest.Case leaf(final Random random) {
        final int pick = random.nextInt(9);
        final MatcherQueryTest.Case result;
        if (pick < 4) {
            final int divisor = MatcherQueryTest.DIVISORS[pick];
            result = MatcherQueryTest.Case.of(
                Query.term(String.format("t%d", divisor)),
                doc -> doc % divisor == 0
            );
        } else if (pick == 4) {
            result = MatcherQueryTest.Case.of(Query.MATCH_ALL, doc -> true);
        } else if (pick == 5) {
            result = MatcherQueryTest.Case.of(Query.term("any"), doc -> true);
        } else {
            result = MatcherQueryTest.value(random, pick);
        }
        return result;
    }

    private static MatcherQueryTest.Case value(
        final Random random, final int pick
    ) {
        final int low = 1 + random.nextInt(MatcherQueryTest.SIZE);
        final int high = low + random.nextInt(12) - 2;
        final String lower = String.format("%02d", low);
        final String upper = String.format("%02d", high);
        final MatcherQueryTest.Case result;
        if (pick == 6) {
            result = MatcherQueryTest.Case.of(
                Query.valueRange(MatcherQueryTest.SLOT, lower, upper),
                doc -> doc % 4 != 0 && doc >= low && doc <= high
            );
        } else if (pick == 7) {
            result = MatcherQueryTest.Case.of(
                Query.value(QueryOp.VALUE_GE, MatcherQueryTest.SLOT, lower),
                doc -> doc % 4 != 0 && doc >= low
            );
        } else {
            result = MatcherQueryTest.Case.of(
                Query.value(QueryOp.VALUE_LE, MatcherQueryTest.SLOT, upper),
                doc -> doc % 4 != 0 && doc <= high
            );
        }
        return new MatcherQueryTest.Case(result.query(), result.docs(), false);
    }

    private static MatcherQueryTest.Case branch(
        final Random random, final int depth
    ) {
        final int pick = random.nextInt(6);
        final MatcherQueryTest.Case left = MatcherQueryTest.tree(random, depth);
        final MatcherQueryTest.Case right = MatcherQueryTest.tree(random, depth);
        final BitSet docs = (BitSet) left.docs().clone();
        boolean exact = left.exact() && right.exact();
        final Query query;
        if (pick == 0) {
            query = Query.of(QueryOp.AND, left.query(), right.query());
            docs.and(right.docs());
        } else if (pick == 1) {
            query = Query.of(QueryOp.OR, left.query(), right.query());
            docs.or(right.docs());
        } else if (pick == 2) {
            query = Query.of(QueryOp.XOR, left.query(), right.query());
            docs.xor(right.docs());
        } else if (pick == 3) {
            query = Query.of(
                QueryOp.AND_NOT, MatcherQueryTest.wrapped(left), right.query()
            );
            docs.andNot(right.docs());
        } else if (pick == 4) {
            query = Query.of(
                QueryOp.AND_MAYBE, MatcherQueryTest.wrapped(left), right.query()
            );
        } else {
            query = Query.scale(2.0, left.query());
            exact = false;
        }
        return new MatcherQueryTest.Case(query, docs, exact);
    }

    private static Query wrapped(final MatcherQueryTest.Case side) {
        return Query.of(QueryOp.AND, side.query(), Query.MATCH_ALL);
    }

    private static List<Integer> stepped(final PostList source) {
        PostList list = source.next();
        final List<Integer> result = new ArrayList<>(0);
        while (!list.atEnd()) {
            result.add(list.docId().value());
            list = list.next();
        }
        return result;
    }

    private static List<Integer> pruned(
        final PostList source, final double least, final int gap
    ) {
        source.recalcMaxWeight();
        final List<Integer> result = new ArrayList<>(0);
        PostList list = source.next(least);
        while (!list.atEnd()) {
            result.add(list.docId().value());
            if (gap == 0) {
                list = list.next(least);
            } else {
                list = list.skipTo(
                    DocId.of(list.docId().value() + gap), least
                );
            }
        }
        return result;
    }

    private static List<Integer> skipped(final PostList source, final int stride) {
        PostList list = source;
        final List<Integer> result = new ArrayList<>(0);
        int target = 1;
        list = list.skipTo(DocId.of(target));
        while (!list.atEnd()) {
            result.add(list.docId().value());
            target = list.docId().value() + stride;
            list = list.skipTo(DocId.of(target));
        }
        return result;
    }

    private static List<Integer> skipping(
        final List<Integer> expected, final int stride
    ) {
        final List<Integer> result = new ArrayList<>(0);
        int target = 1;
        for (final int doc : expected) {
            if (doc >= target) {
                result.add(doc);
                target = doc + stride;
            }
        }
        return result;
    }

    private static void checks(
        final PostList source, final List<Integer> expected, final String name
    ) {
        PostList list = source;
        int target = 1;
        while (target <= MatcherQueryTest.SIZE && !list.atEnd()) {
            final int from = target;
            final int first = expected.stream().filter(doc -> doc >= from)
                .findFirst().orElse(0);
            final Probe probe = list.check(DocId.of(target), 0.0);
            list = probe.list();
            if (probe.valid() && list.atEnd()) {
                Assertions.assertEquals(0, first, name);
            } else if (probe.valid()) {
                final int doc = list.docId().value();
                Assertions.assertTrue(doc >= target, name);
                Assertions.assertTrue(first == 0 || doc <= first, name);
                target = doc;
            }
            target = target + 1;
        }
    }

    private static void bounds(
        final InMemoryShard shard, final Query query, final int count,
        final String name
    ) {
        final MatchEstimates estimates = MatchEstimate.from(
            Matcher.resolve(shard, query)
        ).resolve(
            shard.docCount(), DocId.of(1), shard.lastDocId()
        );
        Assertions.assertTrue(estimates.min().value() <= count, name);
        Assertions.assertTrue(estimates.max().value() >= count, name);
        Assertions.assertTrue(
            estimates.min().value() <= estimates.est().value()
                && estimates.est().value() <= estimates.max().value(),
            name
        );
    }

    /**
     * A query with the documents it matches.
     *
     * @param query Query
     * @param docs Matching documents
     * @param exact True when the estimates bound the matches exactly
     * @since 0.1.0
     */
    private record Case(Query query, BitSet docs, boolean exact) {

        /**
         * A query matching the documents a predicate accepts.
         *
         * @param query Query
         * @param accept Predicate over document numbers
         * @return Case
         */
        static MatcherQueryTest.Case of(
            final Query query, final IntPredicate accept
        ) {
            final BitSet docs = new BitSet();
            IntStream.rangeClosed(1, MatcherQueryTest.SIZE).filter(accept)
                .forEach(docs::set);
            return new MatcherQueryTest.Case(query, docs, true);
        }
    }
}
