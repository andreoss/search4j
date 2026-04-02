/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.PostingScores;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Unweighted union of many posting lists.
 *
 * <p>Members are kept in a binary heap ordered by their current document.
 * The heap shrinks as members end and collapses to the sole survivor.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.NullAssignment")
public final class BoolOrPostList implements PostList {

    /**
     * Current document, {@link DocId#NONE} before the start and at the end.
     */
    private DocId did;

    /**
     * Members in heap order.
     */
    private PostList[] lists;

    /**
     * Current document of each member.
     */
    private DocId[] dids;

    /**
     * Number of members.
     */
    private int size;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree corresponding to the union operation.
     */
    private final MatchEstimate estimate;

    /**
     * Creates an unweighted union.
     *
     * @param members Members
     * @param dbsize Number of documents in the database
     */
    public BoolOrPostList(
        final List<? extends PostList> members, final TermCount dbsize
    ) {
        this(BoolOrSetup.fresh(members, dbsize));
    }

    /**
     * Creates an unweighted union from prepared state.
     *
     * @param setup Prepared members and estimate
     */
    private BoolOrPostList(final BoolOrSetup setup) {
        this(
            setup.lists(), setup.dids(),
            setup.size(), setup.frequency(), setup.estimate()
        );
    }

    /**
     * Creates an unweighted union from its parts.
     *
     * @param lists Members in heap order
     * @param dids Current document of each member
     * @param size Number of members
     * @param frequency Estimated number of matching documents
     * @param estimate Query estimate node
     */
    private BoolOrPostList(
        final PostList[] lists, final DocId[] dids,
        final int size, final TermCount frequency,
        final MatchEstimate estimate
    ) {
        this.lists = lists;
        this.dids = dids;
        this.size = size;
        this.did = DocId.NONE;
        this.frequency = frequency;
        this.estimate = estimate;
    }

    @Override
    public DocId docId() {
        return this.did;
    }

    @Override
    public boolean atEnd() {
        return this.size == 0;
    }

    @Override
    public PostList next(final double least) {
        PostList result = this;
        boolean more = this.size != 0;
        while (more) {
            if (this.dids[0].equals(this.did)) {
                result = this.advance();
                more = result == this;
            } else {
                result = this.settle();
                more = false;
            }
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        PostList result = this;
        if (target.compareTo(this.did) > 0) {
            this.did = new DocId(Integer.MAX_VALUE);
            final PostList terminal = this.prune(target);
            if (terminal == null) {
                result = this.finish();
            } else {
                result = terminal;
            }
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public void gatherPositionLists(final Consumer<PositionList> sink) {
        for (int index = 0; index < this.size; ++index) {
            if (this.dids[index].equals(this.did)) {
                this.lists[index].gatherPositionLists(sink);
            }
        }
    }

    @Override
    public double weight(final DocStats doc) {
        return 0.0;
    }

    @Override
    public double recalcMaxWeight() {
        return 0.0;
    }

    @Override
    public TermCount wdf() {
        return this.forAllMatches(PostingScores::wdf);
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return this.forAllMatches(PostingScores::countMatchingSubqueries);
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        DocRange range = whole;
        if (this.size > 0) {
            range = this.lists[0].docIdRange(whole);
            for (int index = 1; index < this.size; ++index) {
                range = PostListMath.union(
                    range, this.lists[index].docIdRange(whole)
                );
            }
        }
        return range;
    }

    @Override
    public String description() {
        final StringBuilder text = new StringBuilder(32);
        text.append("BoolOrPostList(");
        if (this.size > 0) {
            text.append(this.lists[0].description());
            for (int index = 1; index < this.size; ++index) {
                text.append(", ");
                text.append(this.lists[index].description());
            }
        }
        text.append(')');
        return text.toString();
    }

    /**
     * Estimate operation associated with this list.
     *
     * @return Query estimate node
     */
    MatchEstimate matchEstimate() {
        return this.estimate;
    }

    private PostList advance() {
        PostList result = this;
        this.lists[0] = this.lists[0].next(0.0);
        if (this.lists[0].atEnd()) {
            result = this.drop();
        } else {
            this.dids[0] = this.lists[0].docId();
            this.replace();
        }
        return result;
    }

    private PostList drop() {
        PostList result = this;
        if (this.size == 1) {
            this.size = 0;
            result = this.lists[0];
        } else {
            this.pop();
            this.lists[this.size - 1] = null;
            this.dids[this.size - 1] = null;
            --this.size;
        }
        return result;
    }

    private PostList settle() {
        final PostList result;
        if (this.size == 1) {
            this.size = 0;
            result = this.lists[0];
        } else {
            this.did = this.dids[0];
            result = this;
        }
        return result;
    }

    private PostList finish() {
        final PostList result;
        if (this.size == 1) {
            this.size = 0;
            result = this.lists[0];
        } else {
            this.make();
            result = this;
        }
        return result;
    }

    private PostList prune(final DocId target) {
        PostList terminal = null;
        int live = 0;
        for (int index = 0; index < this.size && terminal == null; ++index) {
            if (this.dids[index].compareTo(target) < 0) {
                terminal = this.advanceTo(index, live, target);
            } else if (live != index) {
                this.lists[live] = this.lists[index];
                this.dids[live] = this.dids[index];
            }
            if (terminal == null && this.lists[live] != null) {
                this.did = PostListMath.min(this.did, this.dids[live]);
                ++live;
            }
        }
        if (terminal == null) {
            this.size = live;
        }
        return terminal;
    }

    private PostList advanceTo(
        final int index, final int live, final DocId target
    ) {
        PostList terminal = null;
        this.lists[live] = this.lists[index].skipTo(target, 0.0);
        if (this.lists[live].atEnd()) {
            if (live == 0 && index == this.size - 1) {
                this.size = 0;
                terminal = this.lists[live];
            } else {
                this.lists[live] = null;
                this.dids[live] = null;
            }
        } else {
            this.dids[live] = this.lists[live].docId();
        }
        return terminal;
    }

    private TermCount forAllMatches(final Function<PostList, TermCount> part) {
        TermCount result = TermCount.ZERO;
        for (int index = 0; index < this.size; ++index) {
            if (this.dids[index].equals(this.did)) {
                result = result.plus(part.apply(this.lists[index]));
            }
        }
        return result;
    }

    private void pop() {
        if (this.size > 1) {
            this.swap(0, this.size - 1);
            this.siftDown(this.size - 1, 0);
        }
    }

    private void replace() {
        this.siftDown(this.size, 0);
    }

    private void make() {
        if (this.size > 1) {
            for (int start = (this.size - 2) / 2; start >= 0; --start) {
                this.siftDown(this.size, start);
            }
        }
    }

    private void siftDown(final int length, final int start) {
        if (length >= 2 && (length - 2) / 2 >= start) {
            this.sift(length, start);
        }
    }

    private void sift(final int length, final int start) {
        int child = this.child(length, start);
        if (this.dids[child].compareTo(this.dids[start]) <= 0) {
            final DocId topid = this.dids[start];
            final PostList toplst = this.lists[start];
            int position = start;
            while (true) {
                this.dids[position] = this.dids[child];
                this.lists[position] = this.lists[child];
                position = child;
                if ((length - 2) / 2 < child) {
                    break;
                }
                child = this.child(length, child);
                if (this.dids[child].compareTo(topid) > 0) {
                    break;
                }
            }
            this.dids[position] = topid;
            this.lists[position] = toplst;
        }
    }

    private int child(final int length, final int parent) {
        int child = 2 * parent + 1;
        if (child + 1 < length
            && this.dids[child].compareTo(this.dids[child + 1]) > 0) {
            ++child;
        }
        return child;
    }

    private void swap(final int first, final int second) {
        final DocId swapid = this.dids[first];
        this.dids[first] = this.dids[second];
        this.dids[second] = swapid;
        final PostList swaplst = this.lists[first];
        this.lists[first] = this.lists[second];
        this.lists[second] = swaplst;
    }
}
