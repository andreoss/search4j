/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link LeafPostList} and the posting list defaults.
 *
 * @since 0.1.0
 */
final class LeafPostListTest {

    /**
     * Statistics of a document scored in the tests.
     */
    private static final DocStats DOC = new DocStats(
        new TermCount(10), new TermCount(4), new TermCount(3)
    );

    @Test
    void keepsTerm() {
        Assertions.assertEquals(
            "apple", new LeafPostListTest.Fixed("apple").term(), "Term listed"
        );
    }

    @Test
    void renamesTerm() {
        final LeafPostList list = new LeafPostListTest.Fixed("apple");
        list.rename("pear");
        Assertions.assertEquals("pear", list.term(), "Renamed");
    }

    @Test
    void rejectsMissingTerm() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new LeafPostListTest.Fixed(null),
            "Null term"
        );
    }

    @Test
    void rejectsMissingRename() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new LeafPostListTest.Fixed("a").rename(null),
            "Null rename"
        );
    }

    @Test
    void scoresNothingUnweighted() {
        Assertions.assertEquals(
            0.0,
            new LeafPostListTest.Fixed("a").weight(LeafPostListTest.DOC),
            "Zero"
        );
    }

    @Test
    void hasNoMaxWeightUnweighted() {
        Assertions.assertEquals(
            0.0, new LeafPostListTest.Fixed("a").recalcMaxWeight(), "Zero max"
        );
    }

    @Test
    void countsNoSubqueryUnweighted() {
        Assertions.assertEquals(
            TermCount.ZERO,
            new LeafPostListTest.Fixed("a").countMatchingSubqueries(),
            "No subquery"
        );
    }

    @Test
    void scoresWithAttachedWeight() {
        Assertions.assertEquals(
            70.0,
            LeafPostListTest.weighted().weight(LeafPostListTest.DOC),
            1.0e-9,
            "Weighted"
        );
    }

    @Test
    void hasMaxWeightOfAttachedWeight() {
        Assertions.assertEquals(
            2.5, LeafPostListTest.weighted().recalcMaxWeight(), "Max weight"
        );
    }

    @Test
    void countsOneSubqueryWeighted() {
        Assertions.assertEquals(
            new TermCount(1),
            LeafPostListTest.weighted().countMatchingSubqueries(),
            "One subquery"
        );
    }

    @Test
    void rejectsSecondWeight() {
        final LeafPostList list = new LeafPostListTest.Fixed("a");
        list.weighting(new LeafPostListTest.Scaled());
        Assertions.assertThrows(
            InvariantError.class,
            () -> list.weighting(new LeafPostListTest.Scaled()),
            "Weight attached twice"
        );
    }

    @Test
    void gathersOwnPositions() {
        final List<PositionList> sink = new ArrayList<>(0);
        new LeafPostListTest.Fixed("a").gatherPositionLists(sink::add);
        final PositionList only = sink.get(0);
        only.next();
        Assertions.assertEquals(
            "1 4", String.format("%d %s", sink.size(), only.position()), "Own"
        );
    }

    @Test
    void checksBySkipping() {
        final Probe probe =
            new LeafPostListTest.Fixed("a").check(new DocId(2), 0.0);
        Assertions.assertEquals(
            "true 3",
            String.format("%b %s", probe.valid(), probe.list().docId()),
            "Valid and skipped"
        );
    }

    @Test
    void movesWithoutLeast() {
        final PostList list = new LeafPostListTest.Fixed("a").next();
        Assertions.assertEquals(
            "1 3",
            String.format(
                "%s %s", list.docId(), list.skipTo(new DocId(3)).docId()
            ),
            "First then skipped"
        );
    }

    @Test
    void keepsWholeRange() {
        final DocRange whole = new DocRange(new DocId(1), new DocId(9));
        Assertions.assertSame(
            whole,
            new LeafPostListTest.Fixed("a").docIdRange(whole),
            "Range unchanged"
        );
    }

    @Test
    void refusesPositionsByDefault() {
        final PostList list = EmptyPostList.INSTANCE;
        Assertions.assertThrows(
            InvalidOperationError.class, list::openPositionList, "Open"
        );
        Assertions.assertThrows(
            UnimplementedError.class, list::readPositionList, "Read"
        );
        Assertions.assertThrows(
            InvariantError.class,
            () -> list.gatherPositionLists(item -> { }),
            "Gather"
        );
        Assertions.assertThrows(
            InvalidOperationError.class, list::wdf, "No frequency"
        );
    }

    private static LeafPostList weighted() {
        final LeafPostList list = new LeafPostListTest.Fixed("a");
        list.weighting(new LeafPostListTest.Scaled());
        return list;
    }

    /**
     * Leaf list over documents 1 and 3, each with frequency 7.
     *
     * @since 0.1.0
     */
    private static final class Fixed extends LeafPostList {

        /**
         * The identifiers listed.
         */
        private final List<DocId> docs;

        /**
         * Index of the current document, minus one before the start.
         */
        private int index;

        /**
         * Creates the list.
         *
         * @param term Term listed
         */
        Fixed(final String term) {
            this(term, List.of(new DocId(1), new DocId(3)));
        }

        /**
         * Creates the list over documents.
         *
         * @param term Term listed
         * @param docs Identifiers listed
         */
        Fixed(final String term, final List<DocId> docs) {
            super(term);
            this.docs = docs;
            this.index = -1;
        }

        @Override
        public TermCount collectionFrequency() {
            return new TermCount(14);
        }

        @Override
        public TermCount wdfUpperBound() {
            return new TermCount(7);
        }

        @Override
        public Optional<LeafPostList> openNearby(
            final String term,
            final boolean positions
        ) {
            return Optional.empty();
        }

        @Override
        public DocId docId() {
            return this.docs.get(this.index);
        }

        @Override
        public boolean atEnd() {
            return this.index >= this.docs.size();
        }

        @Override
        public PostList next(final double least) {
            this.index += 1;
            return this;
        }

        @Override
        public PostList skipTo(final DocId target, final double least) {
            while (this.index < 0 || this.docId().compareTo(target) < 0) {
                this.index += 1;
            }
            return this;
        }

        @Override
        public String description() {
            return "Fixed";
        }

        @Override
        public TermCount termFrequency() {
            return new TermCount(2);
        }

        @Override
        public TermCount wdf() {
            return new TermCount(7);
        }

        @Override
        public PositionList readPositionList() {
            return new VectorPositionList(List.of(new TermPos(4)));
        }
    }

    /**
     * Weight of ten times the frequency, maximum 2.5.
     *
     * @since 0.1.0
     */
    private static final class Scaled implements TermWeight {

        @Override
        public double sumPart(final TermCount wdf, final DocStats doc) {
            return wdf.value() * (double) doc.length().value();
        }

        @Override
        public double maxPart() {
            return 2.5;
        }
    }
}
