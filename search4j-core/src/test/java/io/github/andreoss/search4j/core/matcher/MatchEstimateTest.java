/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Formula checks for the boolean match-estimate tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Reference-derived checks for estimate bounds and boolean operators.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnnecessaryLocalRule",
    "PMD.UnitTestContainsTooManyAsserts"
})
final class MatchEstimateTest {

    /**
     * Collection size in the reference-derived examples.
     */
    private static final TermCount DATABASE_SIZE = new TermCount(10);

    @Test
    void resolvesKnownBoundsAgainstDatabaseBounds() {
        final MatchEstimates actual = MatchEstimate.known(
            new MatchEstimates(
                new TermCount(2), new TermCount(4), new TermCount(7),
                DocId.of(1), DocId.of(10)
            )
        ).resolve(
            MatchEstimateTest.DATABASE_SIZE, DocId.of(3), DocId.of(8)
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(2), new TermCount(4), new TermCount(6),
                DocId.of(3), DocId.of(8)
            ),
            actual,
            "Known max and document range are clipped"
        );
    }

    @Test
    void resolvesIntersectionBoundsAndEstimate() {
        final PostList left = MatchEstimateTest.known("left", 7, 1, 10);
        final PostList right = MatchEstimateTest.known("right", 6, 1, 10);
        final AndPostList intersection = new AndPostList(
            List.of(left, right), () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(4), new TermCount(6),
                DocId.of(1), DocId.of(10)
            ),
            intersection.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "AND minimum, independent estimate, and maximum"
        );
    }

    @Test
    void resolvesThreeChildIntersectionAndExclusiveUnion() {
        final List<PostList> children = List.of(
            MatchEstimateTest.known("first", 7, 1, 10),
            MatchEstimateTest.known("second", 6, 1, 10),
            MatchEstimateTest.known("third", 5, 1, 10)
        );
        final AndPostList intersection = new AndPostList(children, () -> { });
        final XorPostList exclusive = new XorPostList(
            children, MatchEstimateTest.DATABASE_SIZE, () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(0), new TermCount(2), new TermCount(5),
                DocId.of(1), DocId.of(10)
            ),
            intersection.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Three-child AND bounds and independent estimate"
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(0), new TermCount(5), new TermCount(10),
                DocId.of(1), DocId.of(10)
            ),
            exclusive.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Three-child XOR parity bounds and independent estimate"
        );
    }

    @Test
    void resolvesDisjointRangesToNoIntersection() {
        final AndPostList intersection = new AndPostList(
            List.of(
                MatchEstimateTest.known("left", 2, 1, 3),
                MatchEstimateTest.known("right", 2, 8, 10)
            ),
            () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                TermCount.ZERO, TermCount.ZERO, TermCount.ZERO,
                DocId.of(8), DocId.of(3)
            ),
            intersection.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Disjoint document ranges have no intersection"
        );
    }

    @Test
    void resolvesDifferenceBoundsAndEstimate() {
        final AndNotPostList difference = new AndNotPostList(
            MatchEstimateTest.known("included", 7, 1, 10),
            MatchEstimateTest.known("excluded", 3, 5, 10),
            MatchEstimateTest.DATABASE_SIZE
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(4), new TermCount(5), new TermCount(7),
                DocId.of(1), DocId.of(10)
            ),
            difference.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "AND_NOT accounts for overlap and independent rejection"
        );
    }

    @Test
    void resolvesUnionBoundsAndEstimate() {
        final OrPostList union = new OrPostList(
            MatchEstimateTest.known("left", 4, 1, 6),
            MatchEstimateTest.known("right", 3, 5, 10),
            () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(4), new TermCount(6), new TermCount(7),
                DocId.of(1), DocId.of(10)
            ),
            union.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "OR uses pairwise independent estimates"
        );
    }

    @Test
    void resolvesExclusiveUnionParityBoundsAndEstimate() {
        final XorPostList exclusive = new XorPostList(
            List.of(
                MatchEstimateTest.known("left", 4, 1, 6),
                MatchEstimateTest.known("right", 3, 5, 10)
            ),
            MatchEstimateTest.DATABASE_SIZE, () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(1), new TermCount(5), new TermCount(7),
                DocId.of(1), DocId.of(10)
            ),
            exclusive.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "XOR bounds account for parity and independent overlap"
        );
    }

    @Test
    void resolvesExclusiveUnionWhenAChildMatchesEveryDocument() {
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(8), new TermCount(8), new TermCount(8),
                DocId.of(1), DocId.of(10)
            ),
            new XorPostList(
                List.of(
                    MatchEstimateTest.known("all", 10, 1, 10),
                    MatchEstimateTest.known("partial", 2, 1, 10)
                ),
                MatchEstimateTest.DATABASE_SIZE, () -> { }
            ).matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "A universal XOR child inverts the remaining set"
        );
    }

    @Test
    void optionalIntersectionUsesRequiredSideEstimate() {
        final PostList required = new AndPostList(
            List.of(
                MatchEstimateTest.known("first", 7, 1, 10),
                MatchEstimateTest.known("second", 6, 1, 10)
            ),
            () -> { }
        );
        final AndMaybePostList optional = new AndMaybePostList(
            required, MatchEstimateTest.known("optional", 9, 1, 10),
            () -> { }
        );
        Assertions.assertSame(
            MatchEstimate.from(required), optional.matchEstimate(),
            "Optional side does not alter match estimates"
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(4), new TermCount(6),
                DocId.of(1), DocId.of(10)
            ),
            optional.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Required-side estimate passes through"
        );
    }

    @Test
    void decayRetainsTheReferenceEstimateTree() {
        final OrPostList highthr = new OrPostList(
            BooleanPostListFixture.fake("left", 1, 3, 5).maxed(1.0),
            BooleanPostListFixture.fake("right", 2, 3, 6).maxed(2.0),
            () -> { }
        );
        highthr.recalcMaxWeight();
        final PostList andlist = highthr.next(3.0);
        Assertions.assertInstanceOf(AndPostList.class, andlist);
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(5), new TermCount(6),
                DocId.of(1), DocId.of(9)
            ),
            MatchEstimate.from(andlist).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "AND decay retains the originating union estimate"
        );
        final OrPostList skipsrc = new OrPostList(
            BooleanPostListFixture.fake("left", 1, 3, 5).maxed(1.0),
            BooleanPostListFixture.fake("right", 2, 3, 6).maxed(2.0),
            () -> { }
        );
        skipsrc.recalcMaxWeight();
        final PostList skipand = skipsrc.skipTo(DocId.of(3), 3.0);
        Assertions.assertInstanceOf(AndPostList.class, skipand);
        Assertions.assertEquals(
            MatchEstimate.from(andlist).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            MatchEstimate.from(skipand).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "OR skip decay preserves the conjunction estimate"
        );
        final OrPostList checksrc = new OrPostList(
            BooleanPostListFixture.fake("left", 1, 3, 5).maxed(1.0),
            BooleanPostListFixture.fake("right", 2, 3, 6).maxed(2.0),
            () -> { }
        );
        checksrc.recalcMaxWeight();
        final Probe checkand = checksrc.check(DocId.of(3), 3.0);
        Assertions.assertInstanceOf(AndPostList.class, checkand.list());
        Assertions.assertTrue(checkand.valid());
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(5), new TermCount(6),
                DocId.of(1), DocId.of(9)
            ),
            MatchEstimate.from(checkand.list()).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "OR check decay retains the originating estimate tree"
        );
        final PostList left = BooleanPostListFixture.fake("left", 1, 3, 5)
            .maxed(1.0);
        final PostList right = BooleanPostListFixture.fake("right", 2, 3, 6)
            .maxed(3.0);
        final OrPostList source = new OrPostList(left, right, () -> { });
        source.recalcMaxWeight();
        final PostList decayed = source.next(2.0);
        Assertions.assertInstanceOf(AndMaybePostList.class, decayed);
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(5), new TermCount(6),
                DocId.of(1), DocId.of(9)
            ),
            MatchEstimate.from(decayed).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Optional decay keeps the originating union estimate"
        );
        final AndMaybePostList maybe = new AndMaybePostList(
            BooleanPostListFixture.fake("required", 1, 3, 5).maxed(1.0),
            BooleanPostListFixture.fake("optional", 2, 3, 6).maxed(3.0),
            () -> { }
        );
        maybe.recalcMaxWeight();
        final PostList conjunction = maybe.skipTo(DocId.of(1), 2.0);
        Assertions.assertInstanceOf(AndPostList.class, conjunction);
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(3), new TermCount(3),
                DocId.of(1), DocId.of(9)
            ),
            MatchEstimate.from(conjunction).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "AND_MAYBE decay keeps the original required-side estimate"
        );
        final AndMaybePostList checkmaybe = new AndMaybePostList(
            BooleanPostListFixture.fake("required", 1, 3, 5).maxed(1.0),
            BooleanPostListFixture.fake("optional", 2, 3, 6).maxed(3.0),
            () -> { }
        );
        checkmaybe.recalcMaxWeight();
        final Probe checked = checkmaybe.check(DocId.of(3), 2.0);
        Assertions.assertTrue(checked.valid(), "Decayed check matches");
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(3), new TermCount(3),
                DocId.of(1), DocId.of(9)
            ),
            MatchEstimate.from(checked.list()).resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "AND_MAYBE check decay preserves the required estimate"
        );
    }

    @Test
    void composesNestedBooleanEstimateNodes() {
        final PostList nested = new AndPostList(
            List.of(
                MatchEstimateTest.known("first", 7, 1, 10),
                MatchEstimateTest.known("second", 6, 1, 10)
            ),
            () -> { }
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(3), new TermCount(5), new TermCount(8),
                DocId.of(1), DocId.of(10)
            ),
            new OrPostList(
                nested, MatchEstimateTest.known("other", 2, 1, 10),
                () -> { }
            ).matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Parent estimate resolves its nested child estimate"
        );
    }

    @Test
    void booleanOrUsesTheUnionEstimateTree() {
        final BoolOrPostList union = new BoolOrPostList(
            List.of(
                MatchEstimateTest.known("first", 4, 1, 9),
                MatchEstimateTest.known("second", 4, 1, 9),
                MatchEstimateTest.known("third", 4, 1, 9)
            ),
            MatchEstimateTest.DATABASE_SIZE
        );
        Assertions.assertEquals(
            new MatchEstimates(
                new TermCount(4), new TermCount(7), new TermCount(9),
                DocId.of(1), DocId.of(9)
            ),
            union.matchEstimate().resolve(
                MatchEstimateTest.DATABASE_SIZE, DocId.of(1), DocId.of(10)
            ),
            "Unweighted OR shares the query union estimate"
        );
        Assertions.assertEquals(
            new TermCount(8), union.termFrequency(),
            "Existing posting-list frequency remains unchanged"
        );
    }

    private static PostList known(
        final String name, final int count, final int first, final int last
    ) {
        return new BooleanPostListFixtureFake(
            name, List.of(), new TermCount(count),
            new DocRange(DocId.of(first), DocId.of(last)), List.of()
        );
    }
}
