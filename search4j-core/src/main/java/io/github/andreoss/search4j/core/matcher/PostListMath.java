/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Comparator;
import java.util.function.BinaryOperator;

/**
 * Range arithmetic and count rounding shared by the boolean operators.
 *
 * @since 0.1.0
 */
final class PostListMath {

    /**
     * Range from the first identifier to the highest one.
     */
    static final DocRange WHOLE = new DocRange(
        DocId.of(1), new DocId(Integer.MAX_VALUE)
    );

    /**
     * Lower of two identifiers, the first on a tie.
     */
    private static final BinaryOperator<DocId> LOWER =
        BinaryOperator.minBy(Comparator.naturalOrder());

    /**
     * Utility class, no instances.
     */
    private PostListMath() {
    }

    /**
     * Rounds a non-negative estimate to a count.
     *
     * @param value Estimate
     * @return Nearest count, clamped to the count range
     */
    static TermCount rounded(final double value) {
        return new TermCount(
            Math.min(
                (long) (Math.max(0.0, value) + 0.5), TermCount.MAX.value()
            )
        );
    }

    /**
     * Number of identifiers in a range.
     *
     * @param range Range
     * @return Length, negative when the range is inverted
     */
    static long length(final DocRange range) {
        return (long) range.last().value() - range.first().value() + 1L;
    }

    /**
     * Intersection of two ranges.
     *
     * @param left First range
     * @param right Second range
     * @return Narrowest range covering both, possibly inverted
     */
    static DocRange narrow(final DocRange left, final DocRange right) {
        return new DocRange(
            PostListMath.max(left.first(), right.first()),
            PostListMath.min(left.last(), right.last())
        );
    }

    /**
     * Union of two ranges.
     *
     * @param left First range
     * @param right Second range
     * @return Widest range covering both
     */
    static DocRange union(final DocRange left, final DocRange right) {
        return new DocRange(
            PostListMath.min(left.first(), right.first()),
            PostListMath.max(left.last(), right.last())
        );
    }

    /**
     * Higher of two identifiers.
     *
     * @param left First identifier
     * @param right Second identifier
     * @return Higher identifier
     */
    static DocId max(final DocId left, final DocId right) {
        final DocId result;
        if (left.compareTo(right) >= 0) {
            result = left;
        } else {
            result = right;
        }
        return result;
    }

    /**
     * Lower of two identifiers.
     *
     * @param left First identifier
     * @param right Second identifier
     * @return Lower identifier
     */
    static DocId min(final DocId left, final DocId right) {
        return PostListMath.LOWER.apply(left, right);
    }

    /**
     * Highest of three identifiers.
     *
     * @param first First identifier
     * @param second Second identifier
     * @param third Third identifier
     * @return Highest identifier
     */
    static DocId max(final DocId first, final DocId second, final DocId third) {
        return PostListMath.max(PostListMath.max(first, second), third);
    }
}
