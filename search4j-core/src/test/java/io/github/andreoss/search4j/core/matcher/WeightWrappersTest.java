/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the weight-modifying wrapper operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Extra weight and synonym wrappers.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class WeightWrappersTest {

    @Test
    void addsTheExtraWeightToEveryDocument() {
        final BooleanPostListFixtureFake child = BooleanPostListFixture.fake(
            "child", 1, 2
        ).scoring(3.0).maxed(4.0);
        final PostList wrapper = new ExtraWeightPostList(
            child,
            new ExtraWeight() {
                @Override
                public double sumExtra(final DocStats doc) {
                    return 1.5;
                }

                @Override
                public double maxExtra() {
                    return 2.0;
                }
            },
            () -> { }
        );
        wrapper.skipTo(DocId.of(1), 0.0);
        Assertions.assertEquals(
            4.5,
            wrapper.weight(BooleanPostListFixture.doc()),
            1.0e-9,
            "Child weight plus extra"
        );
        Assertions.assertEquals(
            6.0, wrapper.recalcMaxWeight(), 1.0e-9, "Child bound plus extra"
        );
        Assertions.assertEquals(
            "ExtraWeightPostList(child)", wrapper.description(), "Description"
        );
    }

    @Test
    void lowersTheLeastWeightByTheExtra() {
        final BooleanPostListFixtureFake child = BooleanPostListFixture.fake(
            "child", 1
        ).scoring(3.0).maxed(4.0);
        final PostList wrapper = new ExtraWeightPostList(
            child,
            new ExtraWeight() {
                @Override
                public double sumExtra(final DocStats doc) {
                    return 2.0;
                }

                @Override
                public double maxExtra() {
                    return 2.0;
                }
            },
            () -> { }
        );
        wrapper.next(2.0);
        Assertions.assertEquals(
            0.0, child.lastLeast(), 1.0e-9, "Least reduced by the extra"
        );
    }

    @Test
    void synonymWeightsAsOneTermAndCountsOneSubquery() {
        final BooleanPostListFixtureFake child = BooleanPostListFixture.fake(
            "child", 1, 2
        ).scoring(5.0).maxed(9.0).wdf(4);
        final SynonymPostList synonym = new SynonymPostList(
            child, WeightWrappersTest.doubling(), false, () -> { }
        );
        synonym.skipTo(DocId.of(1), 0.0);
        Assertions.assertEquals(
            8.0, synonym.weight(BooleanPostListFixture.doc()), 1.0e-9,
            "Weight from the synonym wdf"
        );
        Assertions.assertEquals(
            12.0, synonym.recalcMaxWeight(), 1.0e-9, "Weight bound"
        );
        Assertions.assertEquals(
            new TermCount(1), synonym.countMatchingSubqueries(),
            "One synonym subquery"
        );
        Assertions.assertEquals(
            "SynonymPostList(child)", synonym.description(), "Description"
        );
    }

    @Test
    void synonymIgnoresTheLeastWeightForInteriorMoves() {
        final BooleanPostListFixtureFake child = BooleanPostListFixture.fake(
            "child", 1
        ).scoring(5.0).maxed(9.0);
        final SynonymPostList synonym = new SynonymPostList(
            child, WeightWrappersTest.doubling(), false, () -> { }
        );
        synonym.next(9.0);
        Assertions.assertEquals(
            0.0, child.lastLeast(), 1.0e-9, "Interior moves ignore the least"
        );
    }

    @Test
    void synonymClampsWdfToDocumentLength() {
        final BooleanPostListFixtureFake child = BooleanPostListFixture.fake(
            "child", 1
        ).scoring(1.0).wdf(40);
        final SynonymPostList synonym = new SynonymPostList(
            child, WeightWrappersTest.identity(), true, () -> { }
        );
        synonym.skipTo(DocId.of(1), 0.0);
        Assertions.assertEquals(
            10.0, synonym.weight(BooleanPostListFixture.doc()), 1.0e-9,
            "Wdf clamped to the document length"
        );
    }

    @Test
    void rejectsNullParts() {
        final PostList child = BooleanPostListFixture.fake("child", 1);
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new ExtraWeightPostList(child, null, () -> { }),
            "Extra weight is required"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> new SynonymPostList(
                child, null, false, () -> { }
            ),
            "Term weight is required"
        );
    }

    private static SynonymWeight doubling() {
        return new DoublingSynonymWeight();
    }

    private static SynonymWeight identity() {
        return new IdentitySynonymWeight();
    }

    /**
     * Synonym weight that doubles each part and ignores document length.
     *
     * @since 0.1.0
     */
    private static final class DoublingSynonymWeight implements SynonymWeight {

        @Override
        public double sumPart(final TermCount wdf, final DocStats doc) {
            return wdf.value() * 2.0;
        }

        @Override
        public double maxPart() {
            return 12.0;
        }

        @Override
        public boolean needsWdf() {
            return true;
        }

        @Override
        public boolean needsDocLength() {
            return false;
        }
    }

    /**
     * Synonym weight that returns each part unchanged.
     *
     * @since 0.1.0
     */
    private static final class IdentitySynonymWeight implements SynonymWeight {

        @Override
        public double sumPart(final TermCount wdf, final DocStats doc) {
            return wdf.value();
        }

        @Override
        public double maxPart() {
            return 40.0;
        }

        @Override
        public boolean needsWdf() {
            return true;
        }

        @Override
        public boolean needsDocLength() {
            return true;
        }
    }
}
