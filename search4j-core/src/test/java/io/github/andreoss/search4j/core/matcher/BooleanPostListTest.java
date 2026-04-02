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
import io.github.andreoss.search4j.core.backend.list.Probe;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of intersections, unions, differences and optional intersections.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class BooleanPostListTest {

    @Test
    void intersectsDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(3, 7),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                )
            ),
            "Documents in both"
        );
    }

    @Test
    void gathersCurrentPositionLists() {
        final List<List<List<TermPos>>> seen = new ArrayList<>(7);
        PostList list = new AndPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1).positions(2, 4),
                BooleanPostListFixture.fake("B", 1).positions(3, 5)
            ),
            () -> { }
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new OrPostList(
            BooleanPostListFixture.fake("A", 1).positions(6),
            BooleanPostListFixture.fake("B", 2).positions(7),
            () -> { }
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1).positions(8),
            BooleanPostListFixture.fake("B", 2).positions(9),
            () -> { }
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new BoolOrPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1).positions(10),
                BooleanPostListFixture.fake("B", 2).positions(11)
            ),
            BooleanPostListFixture.size()
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new OrPostList(
            BooleanPostListFixture.fake("A", 1).positions(12),
            BooleanPostListFixture.fake("B", 1).positions(13),
            () -> { }
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1).positions(14),
            BooleanPostListFixture.fake("B", 1).positions(15),
            () -> { }
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        list = new BoolOrPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1).positions(16),
                BooleanPostListFixture.fake("B", 1).positions(17)
            ),
            BooleanPostListFixture.size()
        );
        list = list.skipTo(DocId.of(1));
        seen.add(BooleanPostListTest.gathered(list));
        Assertions.assertEquals(
            List.of(
                List.of(
                    List.of(new TermPos(2), new TermPos(4)),
                    List.of(new TermPos(3), new TermPos(5))
                ),
                List.of(List.of(new TermPos(6))),
                List.of(List.of(new TermPos(8))),
                List.of(List.of(new TermPos(10))),
                List.of(List.of(new TermPos(12)), List.of(new TermPos(13))),
                List.of(List.of(new TermPos(14)), List.of(new TermPos(15))),
                List.of(List.of(new TermPos(17)), List.of(new TermPos(16)))
            ),
            seen,
            "Gather matching leaves only from the current document"
        );
    }

    @Test
    void skipToRepeatsAndLowerTargetsStayMonotonic() {
        final List<Integer> targets = List.of(1, 1, 2, 2, 4, 4, 9, 2);
        final List<List<DocId>> seen = new ArrayList<>(6);
        seen.add(
            BooleanPostListTest.skipTargets(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    ),
                    () -> { }
                ),
                targets
            )
        );
        seen.add(
            BooleanPostListTest.skipTargets(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                ),
                targets
            )
        );
        seen.add(
            BooleanPostListTest.skipTargets(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                ),
                targets
            )
        );
        seen.add(
            BooleanPostListTest.skipTargets(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                ),
                targets
            )
        );
        seen.add(
            BooleanPostListTest.skipTargets(
                new BoolOrPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size()
                ),
                targets
            )
        );
        seen.add(
            BooleanPostListTest.skipTargets(
                new XorPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    BooleanPostListFixture.size(), () -> { }
                ),
                targets
            )
        );
        Assertions.assertEquals(
            List.of(
                List.of(
                    DocId.of(3), DocId.of(3), DocId.of(3), DocId.of(3),
                    DocId.of(7), DocId.of(7), DocId.NONE, DocId.NONE
                ),
                List.of(
                    DocId.of(1), DocId.of(1), DocId.of(2), DocId.of(2),
                    DocId.of(5), DocId.of(5), DocId.NONE, DocId.NONE
                ),
                List.of(
                    DocId.of(1), DocId.of(1), DocId.of(5), DocId.of(5),
                    DocId.of(5), DocId.of(5), DocId.NONE, DocId.NONE
                ),
                List.of(
                    DocId.of(1), DocId.of(1), DocId.of(3), DocId.of(3),
                    DocId.of(5), DocId.of(5), DocId.NONE, DocId.NONE
                ),
                List.of(
                    DocId.of(1), DocId.of(1), DocId.of(2), DocId.of(2),
                    DocId.of(4), DocId.of(4), DocId.NONE, DocId.NONE
                ),
                List.of(
                    DocId.of(2), DocId.of(2), DocId.of(2), DocId.of(2),
                    DocId.of(4), DocId.of(4), DocId.NONE, DocId.NONE
                )
            ),
            seen,
            "Repeated and backwards targets never move any operator backwards"
        );
    }

    @Test
    void repeatedChecksStayOnPresentDocuments() {
        final List<PostList> lists = List.of(
            new AndPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3, 5),
                    BooleanPostListFixture.fake("B", 2, 3, 6)
                ),
                () -> { }
            ),
            new OrPostList(
                BooleanPostListFixture.fake("A", 1, 3, 5),
                BooleanPostListFixture.fake("B", 2, 3, 6),
                () -> { }
            ),
            new AndNotPostList(
                BooleanPostListFixture.fake("A", 1, 3, 5),
                BooleanPostListFixture.fake("B", 2, 6),
                BooleanPostListFixture.size()
            ),
            new AndMaybePostList(
                BooleanPostListFixture.fake("A", 1, 3, 5),
                BooleanPostListFixture.fake("B", 3, 4),
                () -> { }
            ),
            new BoolOrPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3),
                    BooleanPostListFixture.fake("B", 2, 3)
                ),
                BooleanPostListFixture.size()
            ),
            new XorPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3),
                    BooleanPostListFixture.fake("B", 2, 3),
                    BooleanPostListFixture.fake("C", 3, 4)
                ),
                BooleanPostListFixture.size(), () -> { }
            )
        );
        final List<List<Object>> seen = new ArrayList<>(lists.size());
        for (final PostList list : lists) {
            seen.add(BooleanPostListTest.checked(list));
        }
        Assertions.assertEquals(
            List.of(
                List.of(true, DocId.of(3), true, DocId.of(3)),
                List.of(true, DocId.of(3), true, DocId.of(3)),
                List.of(true, DocId.of(3), true, DocId.of(3)),
                List.of(true, DocId.of(3), true, DocId.of(3)),
                List.of(true, DocId.of(3), true, DocId.of(3)),
                List.of(true, DocId.of(3), true, DocId.of(3))
            ),
            seen,
            "Repeated checks of a present document preserve the match"
        );
    }

    @Test
    void movesWithNonzeroWeightThresholds() {
        Assertions.assertEquals(
            List.of(
                List.of(DocId.of(5), 5.0, true, DocId.of(5), 5.0),
                List.of(DocId.of(5), 5.0, true, DocId.of(5), 5.0),
                List.of(DocId.of(5), 2.0, true, DocId.of(5), 2.0),
                List.of(DocId.of(5), 2.0, true, DocId.of(5), 2.0),
                List.of(DocId.of(5), 0.0, true, DocId.of(5), 0.0),
                List.of(DocId.of(5), 2.0, true, DocId.of(5), 2.0)
            ),
            List.of(
                BooleanPostListTest.weightedMove(
                    new AndPostList(
                        List.of(
                            BooleanPostListFixture.fake("A", 1, 3, 5)
                                .scoring(2.0).maxed(2.0),
                            BooleanPostListFixture.fake("B", 2, 3, 5, 6)
                                .scoring(3.0).maxed(3.0)
                        ),
                        () -> { }
                    )
                ),
                BooleanPostListTest.weightedMove(
                    new OrPostList(
                        BooleanPostListFixture.fake("A", 1, 3, 5)
                            .scoring(2.0).maxed(2.0),
                        BooleanPostListFixture.fake("B", 2, 3, 5, 6, 7)
                            .scoring(3.0).maxed(3.0),
                        () -> { }
                    )
                ),
                BooleanPostListTest.weightedMove(
                    new AndNotPostList(
                        BooleanPostListFixture.fake("A", 1, 3, 5)
                            .scoring(2.0).maxed(2.0),
                        BooleanPostListFixture.fake("B", 2, 3, 6)
                            .scoring(3.0).maxed(3.0),
                        BooleanPostListFixture.size()
                    )
                ),
                BooleanPostListTest.weightedMove(
                    new AndMaybePostList(
                        BooleanPostListFixture.fake("A", 1, 3, 5)
                            .scoring(2.0).maxed(2.0),
                        BooleanPostListFixture.fake("B", 2, 3, 6)
                            .scoring(3.0).maxed(3.0),
                        () -> { }
                    )
                ),
                BooleanPostListTest.weightedMove(
                    new BoolOrPostList(
                        List.of(
                            BooleanPostListFixture.fake("A", 1, 3, 5)
                                .scoring(2.0).maxed(2.0),
                            BooleanPostListFixture.fake("B", 2, 3, 6)
                                .scoring(3.0).maxed(3.0)
                        ),
                        BooleanPostListFixture.size()
                    )
                ),
                BooleanPostListTest.weightedMove(
                    new XorPostList(
                        List.of(
                            BooleanPostListFixture.fake("A", 1, 3, 5)
                                .scoring(2.0).maxed(2.0),
                            BooleanPostListFixture.fake("B", 2, 3, 6)
                                .scoring(3.0).maxed(3.0)
                        ),
                        BooleanPostListFixture.size(), () -> { }
                    )
                )
            ),
            "Weighted skip and check preserve the source streams"
        );
    }

    @Test
    void intersectsThreeDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(7),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        BooleanPostListFixture.fake("C", 1, 4, 7, 8)
                    ),
                    () -> { }
                )
            ),
            "Documents in all three"
        );
    }

    @Test
    void intersectsWithEmptyOrSingle() {
        Assertions.assertEquals(
            List.of(),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B")
                    ),
                    () -> { }
                )
            ),
            "Empty member"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new AndPostList(
                    List.of(BooleanPostListFixture.fake("A", 1, 3, 5, 7)),
                    () -> { }
                )
            ),
            "Single member"
        );
    }

    @Test
    void sumsAndScores() {
        final AndPostList list = new AndPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                    .scoring(2.0).maxed(2.0).freq(3).subs(1),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                    .scoring(3.0).maxed(3.0).freq(4).subs(2)
            ),
            () -> { }
        );
        list.skipTo(DocId.of(3));
        Assertions.assertEquals(5.0, list.weight(BooleanPostListFixture.doc()), 1.0e-9, "Weight");
        Assertions.assertEquals(5.0, list.recalcMaxWeight(), 1.0e-9, "Max");
        Assertions.assertEquals(new TermCount(7), list.wdf(), "Wdf");
        Assertions.assertEquals(
            new TermCount(3), list.countMatchingSubqueries(), "Subqueries"
        );
    }

    @Test
    void narrowsAndRange() {
        final DocRange range = new AndPostList(
            List.of(
                BooleanPostListFixture.fake("A", 1, 3).span(1, 5),
                BooleanPostListFixture.fake("B", 3, 7).span(3, 9)
            ),
            () -> { }
        ).docIdRange(PostListMath.WHOLE);
        Assertions.assertEquals(
            new DocRange(DocId.of(3), DocId.of(5)), range, "Narrowed"
        );
    }

    @Test
    void estimatesAndIntersection() {
        Assertions.assertEquals(
            new TermCount(2),
            new AndPostList(
                List.of(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                ),
                () -> { }
            ).termFrequency(),
            "Estimate"
        );
    }

    @Test
    void unitesDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 2, 3, 5, 6, 7),
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "Documents in either"
        );
    }

    @Test
    void unitesThreeDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 2, 3, 4, 5, 6, 7, 8),
            BooleanPostListFixture.drain(
                new OrPostList(
                    new OrPostList(
                        BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                        BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                        () -> { }
                    ),
                    BooleanPostListFixture.fake("C", 1, 4, 7, 8),
                    () -> { }
                )
            ),
            "Documents in any"
        );
    }

    @Test
    void unitesWithEmptyOrSingle() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B"),
                    () -> { }
                )
            ),
            "Empty right"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A"),
                    BooleanPostListFixture.fake("B", 1, 3, 5, 7),
                    () -> { }
                )
            ),
            "Empty left"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new OrPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 1, 3, 5, 7),
                    () -> { }
                )
            ),
            "Same documents"
        );
    }

    @Test
    void sumsOrScores() {
        final OrPostList list = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(2.0).freq(3).subs(1),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0).freq(4).subs(2),
            () -> { }
        );
        list.recalcMaxWeight();
        list.skipTo(DocId.of(1));
        Assertions.assertEquals(
            2.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Left only"
        );
        list.skipTo(DocId.of(2));
        Assertions.assertEquals(
            3.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Right only"
        );
        list.skipTo(DocId.of(3));
        Assertions.assertEquals(
            5.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Both"
        );
        Assertions.assertEquals(new TermCount(7), list.wdf(), "Both wdf");
        Assertions.assertEquals(
            new TermCount(3), list.countMatchingSubqueries(), "Both subqueries"
        );
        Assertions.assertEquals(5.0, list.recalcMaxWeight(), 1.0e-9, "Max");
    }

    @Test
    void unitesOrRange() {
        final DocRange range = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3).span(1, 5),
            BooleanPostListFixture.fake("B", 3, 7).span(3, 9),
            () -> { }
        ).docIdRange(PostListMath.WHOLE);
        Assertions.assertEquals(
            new DocRange(DocId.of(1), DocId.of(9)), range, "United"
        );
    }

    @Test
    void estimatesOrUnion() {
        Assertions.assertEquals(
            new TermCount(6),
            new OrPostList(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                () -> { }
            ).termFrequency(),
            "Estimate"
        );
        Assertions.assertEquals(
            new TermCount(4),
            new OrPostList(
                BooleanPostListFixture.fake("left", 1, 3).span(1, 3),
                BooleanPostListFixture.fake("right", 8, 10).span(8, 10),
                () -> { }
            ).termFrequency(),
            "Disjoint ranges add their estimates"
        );
    }

    @Test
    void orDecaysToAnd() {
        final AtomicInteger updates = new AtomicInteger();
        final OrPostList list = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(2.0),
            updates::incrementAndGet
        );
        list.recalcMaxWeight();
        PostList result = list.next(3.0);
        Assertions.assertInstanceOf(AndPostList.class, result, "Decayed to AND");
        Assertions.assertEquals(DocId.of(3), result.docId(), "First match");
        Assertions.assertEquals(
            5.0, result.weight(BooleanPostListFixture.doc()), 1.0e-9, "Score"
        );
        Assertions.assertTrue(updates.get() > 0, "Decay recalculates weights");
        result = result.next();
        Assertions.assertEquals(DocId.of(7), result.docId(), "Second match");
        final OrPostList skipped = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(2.0),
            () -> { }
        );
        skipped.recalcMaxWeight();
        final PostList moved = skipped.skipTo(DocId.of(3), 3.0);
        Assertions.assertInstanceOf(AndPostList.class, moved, "Skip decay");
        Assertions.assertEquals(DocId.of(3), moved.docId(), "Skip match");
        final OrPostList checked = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(2.0),
            () -> { }
        );
        checked.recalcMaxWeight();
        final Probe probe = checked.check(DocId.of(3), 3.0);
        Assertions.assertInstanceOf(AndPostList.class, probe.list(), "Check decay");
        Assertions.assertTrue(probe.valid(), "Check match");
        Assertions.assertEquals(DocId.of(3), probe.list().docId(), "Check id");
    }

    @Test
    void orDecaysToAndMaybe() {
        final AtomicInteger updates = new AtomicInteger();
        final OrPostList list = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0),
            updates::incrementAndGet
        );
        list.recalcMaxWeight();
        PostList result = list.next(2.0);
        Assertions.assertInstanceOf(
            AndMaybePostList.class, result, "Decayed to optional"
        );
        final List<DocId> docs = new ArrayList<>(4);
        final List<Double> weights = new ArrayList<>(4);
        while (!result.atEnd()) {
            docs.add(result.docId());
            weights.add(result.weight(BooleanPostListFixture.doc()));
            result = result.next(2.0);
        }
        Assertions.assertEquals(
            BooleanPostListFixture.ids(2, 3, 6, 7), docs, "Surviving documents"
        );
        Assertions.assertEquals(List.of(3.0, 5.0, 3.0, 5.0), weights, "Scores");
        Assertions.assertTrue(updates.get() > 0, "Decay recalculates weights");
        final OrPostList skipped = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0),
            () -> { }
        );
        skipped.recalcMaxWeight();
        final PostList moved = skipped.skipTo(DocId.of(3), 2.0);
        Assertions.assertInstanceOf(AndMaybePostList.class, moved, "Skip decay");
        Assertions.assertEquals(DocId.of(3), moved.docId(), "Skip match");
        final OrPostList checked = new OrPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0),
            () -> { }
        );
        checked.recalcMaxWeight();
        final Probe probe = checked.check(DocId.of(3), 2.0);
        Assertions.assertTrue(probe.valid(), "Checked match");
        Assertions.assertEquals(DocId.of(3), probe.list().docId(), "Checked id");
        Assertions.assertEquals(
            5.0, probe.list().weight(BooleanPostListFixture.doc()),
            1.0e-9, "Checked score"
        );
    }

    @Test
    void andMaybeDecaysToAndForWeightedSkipAndCheck() {
        final AtomicInteger skipcount = new AtomicInteger();
        final AndMaybePostList skipsrc = BooleanPostListTest.weightedMaybe(
            skipcount::incrementAndGet
        );
        skipsrc.recalcMaxWeight();
        final PostList skipped = skipsrc.skipTo(DocId.of(3), 2.0);
        Assertions.assertInstanceOf(AndPostList.class, skipped, "Skip decay");
        Assertions.assertTrue(skipcount.get() > 0, "Skip recalculates weights");
        Assertions.assertEquals(DocId.of(3), skipped.docId(), "Skip match");
        Assertions.assertEquals(
            5.0, skipped.weight(BooleanPostListFixture.doc()), 1.0e-9,
            "Skip score"
        );
        final AtomicInteger checkcount = new AtomicInteger();
        final AndMaybePostList checksrc = BooleanPostListTest.weightedMaybe(
            checkcount::incrementAndGet
        );
        checksrc.recalcMaxWeight();
        final Probe checked = checksrc.check(DocId.of(3), 2.0);
        Assertions.assertInstanceOf(AndPostList.class, checked.list(), "Check decay");
        Assertions.assertTrue(checkcount.get() > 0, "Check recalculates weights");
        Assertions.assertTrue(checked.valid(), "Check match");
        Assertions.assertEquals(DocId.of(3), checked.list().docId(), "Check id");
        Assertions.assertEquals(
            5.0, checked.list().weight(BooleanPostListFixture.doc()),
            1.0e-9, "Check score"
        );
    }

    @Test
    void subtractsDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 5),
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    BooleanPostListFixture.size()
                )
            ),
            "Documents only on the left"
        );
    }

    @Test
    void subtractsWithEmpty() {
        Assertions.assertEquals(
            List.of(),
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A"),
                    BooleanPostListFixture.fake("B", 1),
                    BooleanPostListFixture.size()
                )
            ),
            "Empty left"
        );
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new AndNotPostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B"),
                    BooleanPostListFixture.size()
                )
            ),
            "Empty right"
        );
    }

    @Test
    void keepsAndNotScores() {
        final AndNotPostList list = new AndNotPostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(2.0).freq(3).subs(1),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0).freq(4).subs(2),
            BooleanPostListFixture.size()
        );
        list.skipTo(DocId.of(1));
        Assertions.assertEquals(2.0, list.weight(BooleanPostListFixture.doc()), 1.0e-9, "Weight");
        Assertions.assertEquals(2.0, list.recalcMaxWeight(), 1.0e-9, "Max");
        Assertions.assertEquals(new TermCount(3), list.wdf(), "Wdf");
        Assertions.assertEquals(
            new TermCount(1), list.countMatchingSubqueries(), "Subqueries"
        );
    }

    @Test
    void estimatesAndNot() {
        Assertions.assertEquals(
            new TermCount(2),
            new AndNotPostList(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                BooleanPostListFixture.size()
            ).termFrequency(),
            "Estimate"
        );
    }

    @Test
    void rejectsEmptyAndNotDatabase() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new AndNotPostList(
                BooleanPostListFixture.fake("A"),
                BooleanPostListFixture.fake("B"),
                TermCount.ZERO
            ),
            "Zero database"
        );
    }

    @Test
    void keepsMaybeDocuments() {
        Assertions.assertEquals(
            BooleanPostListFixture.ids(1, 3, 5, 7),
            BooleanPostListFixture.drain(
                new AndMaybePostList(
                    BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                    BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                    () -> { }
                )
            ),
            "Every left document"
        );
    }

    @Test
    void sumsMaybeScores() {
        final AndMaybePostList list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7)
                .scoring(2.0).maxed(2.0).freq(3).subs(1),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7)
                .scoring(3.0).maxed(3.0).freq(4).subs(2),
            () -> { }
        );
        list.skipTo(DocId.of(1));
        Assertions.assertEquals(
            2.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Left only"
        );
        list.skipTo(DocId.of(3));
        Assertions.assertEquals(
            5.0, list.weight(BooleanPostListFixture.doc()),
            1.0e-9, "Both"
        );
        Assertions.assertEquals(new TermCount(7), list.wdf(), "Both wdf");
        Assertions.assertEquals(
            new TermCount(3), list.countMatchingSubqueries(), "Both subqueries"
        );
        Assertions.assertEquals(5.0, list.recalcMaxWeight(), 1.0e-9, "Max");
    }

    @Test
    void estimatesMaybe() {
        Assertions.assertEquals(
            new TermCount(4),
            new AndMaybePostList(
                BooleanPostListFixture.fake("A", 1, 3, 5, 7),
                BooleanPostListFixture.fake("B", 2, 3, 6, 7),
                () -> { }
            ).termFrequency(),
            "Estimate"
        );
    }

    @Test
    void maybeDecaysToAnd() {
        final AndMaybePostList list = new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1, 3, 5, 7).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6, 7).maxed(2.0),
            () -> { }
        );
        list.recalcMaxWeight();
        Assertions.assertInstanceOf(
            AndPostList.class, list.next(2.0), "Decayed to intersection"
        );
    }

    private static List<List<TermPos>> gathered(final PostList source) {
        final List<List<TermPos>> result = new ArrayList<>(0);
        source.gatherPositionLists(
            positions -> result.add(
                BooleanPostListFixture.readPositions(positions)
            )
        );
        return result;
    }

    private static List<DocId> skipTargets(
        final PostList source, final List<Integer> targets
    ) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(targets.size());
        for (final int target : targets) {
            list = list.skipTo(DocId.of(target));
            if (list.atEnd()) {
                result.add(DocId.NONE);
            } else {
                result.add(list.docId());
            }
        }
        return result;
    }

    private static List<Object> weightedMove(final PostList source) {
        final double least = 0.5;
        source.recalcMaxWeight();
        final PostList skipped = source.skipTo(DocId.of(4), least);
        final Probe checked = skipped.check(DocId.of(5), least);
        return List.of(
            skipped.docId(), skipped.weight(BooleanPostListFixture.doc()),
            checked.valid(), checked.list().docId(),
            checked.list().weight(BooleanPostListFixture.doc())
        );
    }

    private static AndMaybePostList weightedMaybe(final Runnable recalc) {
        return new AndMaybePostList(
            BooleanPostListFixture.fake("A", 1, 3, 5)
                .scoring(2.0).maxed(1.0),
            BooleanPostListFixture.fake("B", 2, 3, 6)
                .scoring(3.0).maxed(3.0),
            recalc
        );
    }

    private static List<Object> checked(final PostList source) {
        final Probe first = source.check(DocId.of(3), 0.0);
        final Probe repeated = first.list().check(DocId.of(3), 0.0);
        return List.of(
            first.valid(), first.list().docId(),
            repeated.valid(), repeated.list().docId()
        );
    }
}
