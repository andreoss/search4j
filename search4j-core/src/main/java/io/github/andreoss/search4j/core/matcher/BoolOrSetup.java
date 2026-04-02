/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Arrays;
import java.util.List;

/**
 * Prepared state of an unweighted union.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.MethodReturnsInternalArray")
final class BoolOrSetup {

    /**
     * Members in heap order.
     */
    private final PostList[] lists;

    /**
     * Current document of each member.
     */
    private final DocId[] dids;

    /**
     * Number of members.
     */
    private final int size;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree associated with the union.
     */
    private final MatchEstimate matching;

    /**
     * Creates prepared state.
     *
     * @param lists Members in heap order
     * @param dids Current document of each member
     * @param size Number of members
     * @param frequency Estimated number of matching documents
     * @param estimate Query estimate node
     */
    private BoolOrSetup(
        final PostList[] lists, final DocId[] dids,
        final int size, final TermCount frequency,
        final MatchEstimate estimate
    ) {
        this.lists = lists;
        this.dids = dids;
        this.size = size;
        this.frequency = frequency;
        this.matching = estimate;
    }

    PostList[] lists() {
        return this.lists;
    }

    DocId[] dids() {
        return this.dids;
    }

    int size() {
        return this.size;
    }

    TermCount frequency() {
        return this.frequency;
    }

    MatchEstimate estimate() {
        return this.matching;
    }

    /**
     * Prepares the members and estimate of an unweighted union.
     *
     * @param members Members
     * @param dbsize Number of documents in the database
     * @return Prepared state
     */
    static BoolOrSetup fresh(
        final List<? extends PostList> members, final TermCount dbsize
    ) {
        if (dbsize.value() <= 0) {
            throw new InvalidArgumentError("Database size must be positive");
        }
        final PostList[] lists = members.toArray(PostList[]::new);
        final DocId[] dids = new DocId[lists.length];
        Arrays.fill(dids, DocId.NONE);
        return new BoolOrSetup(
            lists, dids, lists.length,
            BoolOrSetup.estimate(lists, lists.length, dbsize),
            MatchEstimate.union(Arrays.asList(lists))
        );
    }

    private static TermCount estimate(
        final PostList[] lists, final int size, final TermCount dbsize
    ) {
        TermCount result = TermCount.ZERO;
        if (size > 0) {
            final double scale = 1.0 / dbsize.value();
            double probability = lists[0].termFrequency().value() * scale;
            for (int index = 1; index < size; ++index) {
                final double current =
                    lists[index].termFrequency().value() * scale;
                probability += current - probability * current;
            }
            result = PostListMath.rounded(probability * dbsize.value());
        }
        return result;
    }
}
