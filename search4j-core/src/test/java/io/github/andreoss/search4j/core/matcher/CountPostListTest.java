/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of postlists over counts above the signed range.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Within-document frequencies and sizes at and above two to the thirty-one,
 * summed in unsigned 32-bit arithmetic as the reference sums them.
 *
 * @since 0.8.0
 */
final class CountPostListTest {

    @Test
    void sumsWdfsOfUnionsModuloTwoToTheThirtyTwo() {
        final PostList union = new OrPostList(
            BooleanPostListFixture.fake("left", 1).freq(3_000_000_000L),
            BooleanPostListFixture.fake("right", 1).freq(2_000_000_000L),
            () -> { }
        );
        union.skipTo(DocId.of(1));
        final PostList both = new AndPostList(
            List.of(
                BooleanPostListFixture.fake("left", 1).freq(2_147_483_648L),
                BooleanPostListFixture.fake("right", 1).freq(2_147_483_647L)
            ),
            () -> { }
        );
        both.skipTo(DocId.of(1));
        Assertions.assertEquals(
            List.of(TermCount.parse("705032704"), TermCount.MAX),
            List.of(union.wdf(), both.wdf()),
            "Sums of unsigned wdfs, wrapped past the maximum"
        );
    }

    @Test
    void takesTheSmallestUnsignedWdfOfAPhrase() {
        final List<PostList> terms = List.of(
            BooleanPostListFixture.fake("left", 1).positions(1, 4)
                .freq(4_000_000_000L).subs(1),
            BooleanPostListFixture.fake("right", 1).positions(2, 5)
                .freq(3_000_000_000L).subs(1)
        );
        final PhrasePostList phrase = new PhrasePostList(
            new AndPostList(terms, () -> { }), new TermPos(2), terms
        );
        phrase.skipTo(DocId.of(1));
        Assertions.assertEquals(
            TermCount.parse("3000000000"), phrase.wdf(),
            "Minimum of wdfs above 2^31"
        );
    }

    @Test
    void sumsPositionListSizesModuloTwoToTheThirtyTwo() {
        Assertions.assertEquals(
            new TermCount(1),
            new OrPositionList(
                List.of(
                    new CountPostListTest.Sized(
                        TermCount.parse("4294967295"),
                        new VectorPositionList(List.of(TermPos.FIRST))
                    ),
                    new CountPostListTest.Sized(
                        new TermCount(2), new VectorPositionList(List.of(TermPos.FIRST))
                    )
                )
            ).approxSize(),
            "Size estimate wrapped"
        );
    }

    @Test
    void roundsEstimatesWithinTheUnsignedRange() {
        Assertions.assertEquals(
            List.of(TermCount.parse("3000000000"), TermCount.MAX),
            List.of(PostListMath.rounded(2_999_999_999.6), PostListMath.rounded(1e12)),
            "Rounded to nearest, clamped to 2^32 - 1"
        );
    }

    /**
     * A single position with a given size estimate.
     *
     * @since 0.8.0
     */
    private static final class Sized implements PositionList {

        /**
         * Size estimate reported.
         */
        private final TermCount size;

        /**
         * Positions walked.
         */
        private final PositionList inner;

        /**
         * Creates a list.
         *
         * @param size Size estimate reported
         * @param inner Positions walked
         */
        Sized(final TermCount size, final PositionList inner) {
            this.size = size;
            this.inner = inner;
        }

        @Override
        public TermCount approxSize() {
            return this.size;
        }

        @Override
        public TermPos back() {
            return this.inner.back();
        }

        @Override
        public TermPos position() {
            return this.inner.position();
        }

        @Override
        public boolean next() {
            return this.inner.next();
        }

        @Override
        public boolean skipTo(final TermPos target) {
            return this.inner.skipTo(target);
        }
    }
}
