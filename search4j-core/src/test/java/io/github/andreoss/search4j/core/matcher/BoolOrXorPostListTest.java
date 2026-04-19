/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the boolean posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of unweighted and exclusive unions and of the shared iteration.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class BoolOrXorPostListTest {

    @Test
    void unitesBoolOrDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 2, 3, 4, 5, 6, 7, 8),
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            "Every document"
        );
    }

    @Test
    void handlesBoolOrEmptyAndSingle() {
        Assertions.assertEquals(
            List.of(),
            BooleanPostListFixture.drain(
                new BoolOrPostList(List.of(), BooleanPostListFixture.size())
            ),
            "Empty"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(BooleanPostListFixture.fake("A", 1, 3, 5, 7)),
                    BooleanPostListFixture.size()
                )
            ),
            "Single"
        );
    }

    @Test
    void countsBoolOrMatches() {
        final BoolOrPostList list = new BoolOrPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7).freq(3).subs(1),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7).freq(4).subs(2),
                BooleanPostListFixture.fake("C", 1, 4, 7, 8).freq(2).subs(4)
            ),
            BooleanPostListFixture.size()
        );
        list.skipTo(DocId.of(7));
        Assertions.assertEquals(new TermCount(9), list.wdf(), "All wdf");
        Assertions.assertEquals(
            new TermCount(7), list.countMatchingSubqueries(), "All subqueries"
        );
        final BoolOrPostList pair = new BoolOrPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7).freq(3).subs(1),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7).freq(4).subs(2)
            ),
            BooleanPostListFixture.size()
        );
        pair.skipTo(DocId.of(3));
        Assertions.assertEquals(new TermCount(7), pair.wdf(), "Pair wdf");
        Assertions.assertEquals(
            new TermCount(3), pair.countMatchingSubqueries(), "Pair subqueries"
        );
    }

    @Test
    void unitesBoolOrRange() {
        final DocRange range = new BoolOrPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3).span(1, 3),
                BooleanPostListFixture.fake("B", 2, 7).span(2, 8),
                BooleanPostListFixture.fake("C", 4, 6).span(4, 6)
            ),
            BooleanPostListFixture.size()
        ).docIdRange(PostListMath.WHOLE);
        Assertions.assertEquals(
            new DocRange(DocId.of(1), DocId.of(8)), range, "United"
        );
    }

    @Test
    void estimatesBoolOrUnion() {
        Assertions.assertEquals(
            new TermCount(8),
            new BoolOrPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                ),
                BooleanPostListFixture.size()
            ).termFrequency(),
            "Estimate"
        );
    }

    @Test
    void selectsOddXorDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 2, 5, 6),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Odd membership"
        );
    }

    @Test
    void selectsOddXorOfThree() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(2, 4, 5, 6, 7, 8),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Odd membership"
        );
    }

    @Test
    void handlesXorEmptyAndSingle() {
        Assertions.assertEquals(
            List.of(),
            BooleanPostListFixture.drain(
                new XorPostList(List.of(), BooleanPostListFixture.size(), () -> { })
            ),
            "Empty"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(BooleanPostListFixture.fake("A", 1, 3, 5, 7)),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Single"
        );
        Assertions.assertEquals(
            List.of(),
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 1, 3, 5, 7)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Same documents"
        );
    }

    @Test
    void sumsXorScores() {
        final XorPostList list = new XorPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                    .scoring(2.0).freq(3).subs(1),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    .scoring(3.0).freq(4).subs(2),
                BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    .scoring(4.0).freq(2).subs(4)
            ),
            BooleanPostListFixture.size(),
            () -> { }
        );
        list.skipTo(DocId.of(2));
        Assertions.assertEquals(
            3.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Single weight"
        );
        Assertions.assertEquals(new TermCount(4), list.wdf(), "Single wdf");
        Assertions.assertEquals(
            new TermCount(2), list.countMatchingSubqueries(), "Single subqueries"
        );
        list.skipTo(DocId.of(7));
        Assertions.assertEquals(
            9.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "All weights"
        );
        Assertions.assertEquals(new TermCount(9), list.wdf(), "All wdf");
        Assertions.assertEquals(
            new TermCount(7), list.countMatchingSubqueries(), "All subqueries"
        );
    }

    @Test
    void computesXorMaxWeightParity() {
        final XorPostList pair = new XorPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1).maxed(2.0),
                BooleanPostListFixture.fake("B", 2).maxed(3.0)
            ),
            BooleanPostListFixture.size(),
            () -> { }
        );
        Assertions.assertEquals(3.0, pair.recalcMaxWeight(), 1.0e-9, "Even");
        final XorPostList triple = new XorPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1).maxed(2.0),
                BooleanPostListFixture.fake("B", 2).maxed(3.0),
                BooleanPostListFixture.fake("C", 3).maxed(4.0)
            ),
            BooleanPostListFixture.size(),
            () -> { }
        );
        Assertions.assertEquals(9.0, triple.recalcMaxWeight(), 1.0e-9, "Odd");
        final XorPostList single = new XorPostList(
            List.of(BooleanPostListFixture.fake("A", 1).maxed(2.0)),
            BooleanPostListFixture.size(),
            () -> { }
        );
        Assertions.assertEquals(2.0, single.recalcMaxWeight(), 1.0e-9, "One");
    }

    @Test
    void recalculatesXorOnReplacement() {
        final AtomicInteger count = new AtomicInteger();
        final XorPostList list = new XorPostList(
            List.of(
                new BooleanPostListFixtureReplacing(
                    BooleanPostListFixture.fake("A", 1, 3), count
                ),
                new BooleanPostListFixtureReplacing(
                    BooleanPostListFixture.fake("B", 2), count
                )
            ),
            BooleanPostListFixture.size(),
            count::incrementAndGet
        );
        BooleanPostListFixture.drain(list);
        Assertions.assertTrue(count.get() > 0, "Replacement recalculated");
    }

    @Test
    void recalculatesOrWhenLeftEnds() {
        final AtomicInteger count = new AtomicInteger();
        BooleanPostListFixture.drain(
            new OrPostList(
                BooleanPostListFixture.fake("A", 1, 3),
                BooleanPostListFixture.fake("B", 2, 5, 7),
                count::incrementAndGet
            )
        );
        Assertions.assertEquals(1, count.get(), "Right kept");
    }

    @Test
    void recalculatesOrWhenRightEnds() {
        final AtomicInteger count = new AtomicInteger();
        BooleanPostListFixture.drain(
            new OrPostList(
                BooleanPostListFixture.fake("A", 2, 5, 7),
                BooleanPostListFixture.fake("B", 1, 3),
                count::incrementAndGet
            )
        );
        Assertions.assertEquals(1, count.get(), "Left kept");
    }

    @Test
    void unitesXorRange() {
        final DocRange range = new XorPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3).span(1, 5),
                BooleanPostListFixture.fake("B", 3, 7).span(3, 9)
            ),
            BooleanPostListFixture.size(),
            () -> { }
        ).docIdRange(PostListMath.WHOLE);
        Assertions.assertEquals(
            new DocRange(DocId.of(1), DocId.of(9)), range, "United"
        );
    }

    @Test
    void estimatesXorUnion() {
        Assertions.assertEquals(
            new TermCount(5),
            new XorPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                ),
                BooleanPostListFixture.size(),
                () -> { }
            ).termFrequency(),
            "Pair estimate"
        );
        Assertions.assertEquals(
            new TermCount(5),
            new XorPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                ),
                BooleanPostListFixture.size(),
                () -> { }
            ).termFrequency(),
            "Triple estimate"
        );
    }

    @Test
    void skipsLikeNext() {
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                )
            ),
            BooleanPostListFixture.skip(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                )
            ),
            "And"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            BooleanPostListFixture.skip(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "Or"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                )
            ),
            BooleanPostListFixture.skip(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                )
            ),
            "AndNot"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            BooleanPostListFixture.skip(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "AndMaybe"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            BooleanPostListFixture.skip(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            "BoolOr"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            BooleanPostListFixture.skip(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Xor"
        );
    }

    @Test
    void checksLikeNext() {
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                )
            ),
            BooleanPostListFixture.check(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                )
            ),
            "And"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            BooleanPostListFixture.check(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "Or"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                )
            ),
            BooleanPostListFixture.check(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                )
            ),
            "AndNot"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            BooleanPostListFixture.check(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "AndMaybe"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            BooleanPostListFixture.check(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                )
            ),
            "BoolOr"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.drain(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            BooleanPostListFixture.check(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(),
                    () -> { }
                )
            ),
            "Xor"
        );
    }

    @Test
    void describesOperators() {
        final PostList one = BooleanPostListFixture.fake("A", 1);
        final PostList two = BooleanPostListFixture.fake("B", 2);
        Assertions.assertEquals(
            "(A AND B)",
            new AndPostList(List.of(one, two), () -> { }).description(),
            "And"
        );
        Assertions.assertEquals(
            "OrPostList(A, B)",
            new OrPostList(one, two, () -> { }).description(), "Or"
        );
        Assertions.assertEquals(
            "AndNotPostList(A, B)",
            new AndNotPostList(one, two, BooleanPostListFixture.size()).description(), "AndNot"
        );
        Assertions.assertEquals(
            "AndMaybePostList(A, B)",
            new AndMaybePostList(one, two, () -> { }).description(), "AndMaybe"
        );
        Assertions.assertEquals(
            "BoolOrPostList(A, B)",
            new BoolOrPostList(List.of(one, two), BooleanPostListFixture.size()).description(),
            "BoolOr"
        );
        Assertions.assertEquals(
            "(A XOR B)",
            new XorPostList(
                List.of(one, two), BooleanPostListFixture.size(), () -> { }
            ).description(),
            "Xor"
        );
    }
}
