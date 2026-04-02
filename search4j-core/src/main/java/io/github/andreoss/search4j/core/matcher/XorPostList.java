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
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;

/**
 * Exclusive union of posting lists.
 *
 * <p>A document matches when an odd number of members contain it. A single
 * surviving member is returned directly.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnnecessaryLocalRule")
public final class XorPostList implements PostList {

    /**
     * Current document, {@link DocId#NONE} before the start and at the end.
     */
    private DocId did;

    /**
     * Whether iteration has started.
     */
    private boolean began;

    /**
     * Members still taking part in the union.
     */
    private PostList[] lists;

    /**
     * Number of members still taking part in the union.
     */
    private int size;

    /**
     * Receiver told when the maximum weight changes.
     */
    private final Runnable recalc;

    /**
     * Estimated number of matching documents.
     */
    private final TermCount frequency;

    /**
     * Estimate tree corresponding to the exclusive union operation.
     */
    private final MatchEstimate estimate;

    /**
     * Creates an exclusive union.
     *
     * @param members Members
     * @param dbsize Number of documents in the database
     * @param recalculation Receiver told when the maximum weight changes
     */
    public XorPostList(
        final List<? extends PostList> members,
        final TermCount dbsize,
        final Runnable recalculation
    ) {
        this(XorSetup.fresh(members, dbsize, recalculation));
    }

    /**
     * Creates an exclusive union from prepared state.
     *
     * @param setup Prepared members and estimate
     */
    private XorPostList(final XorSetup setup) {
        this(
            setup.lists(), setup.size(),
            setup.frequency(), setup.recalc(), setup.estimate()
        );
    }

    /**
     * Creates an exclusive union from its parts.
     *
     * @param lists Members
     * @param size Number of members
     * @param frequency Estimated number of matching documents
     * @param recalc Receiver told when the maximum weight changes
     * @param estimate Query estimate node
     */
    private XorPostList(
        final PostList[] lists, final int size,
        final TermCount frequency, final Runnable recalc,
        final MatchEstimate estimate
    ) {
        this.lists = lists;
        this.size = size;
        this.did = DocId.NONE;
        this.began = false;
        this.recalc = recalc;
        this.frequency = frequency;
        this.estimate = estimate;
    }

    @Override
    public DocId docId() {
        return this.did;
    }

    @Override
    public boolean atEnd() {
        return this.began && !this.did.valid();
    }

    @Override
    public PostList next(final double least) {
        this.began = true;
        PostList result = this;
        boolean more = true;
        while (more) {
            final DocId old = this.did;
            this.did = DocId.NONE;
            final int matching = this.scanNext(old);
            if (this.size == 1) {
                this.size = 0;
                result = this.lists[0];
                more = false;
            } else if (!this.did.valid() || (matching & 1) == 1) {
                more = false;
            }
        }
        return result;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.began = true;
        final DocId old = this.did;
        this.did = DocId.NONE;
        final int matching = this.scanSkip(old, target);
        PostList result = this;
        if (this.size == 1) {
            this.size = 0;
            result = this.lists[0];
        } else if (this.did.valid() && (matching & 1) == 0) {
            result = this.next(least);
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public double weight(final DocStats doc) {
        double result = 0.0;
        if (this.did.valid()) {
            for (int index = 0; index < this.size; ++index) {
                if (this.lists[index].docId().equals(this.did)) {
                    result += this.lists[index].weight(doc);
                }
            }
        }
        return result;
    }

    @Override
    public double recalcMaxWeight() {
        double result = 0.0;
        if (this.size > 0) {
            double least = this.lists[0].recalcMaxWeight();
            double total = least;
            for (int index = 1; index < this.size; ++index) {
                final double current = this.lists[index].recalcMaxWeight();
                if (current < least) {
                    least = current;
                }
                total += current;
            }
            if ((this.size & 1) == 0) {
                total -= least;
            }
            result = total;
        }
        return result;
    }

    @Override
    public TermCount wdf() {
        TermCount result = TermCount.ZERO;
        if (this.did.valid()) {
            for (int index = 0; index < this.size; ++index) {
                if (this.lists[index].docId().equals(this.did)) {
                    result = result.plus(this.lists[index].wdf());
                }
            }
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        TermCount result = TermCount.ZERO;
        if (this.did.valid()) {
            for (int index = 0; index < this.size; ++index) {
                if (this.lists[index].docId().equals(this.did)) {
                    result = result.plus(
                        this.lists[index].countMatchingSubqueries()
                    );
                }
            }
        }
        return result;
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
        text.append('(');
        if (this.size > 0) {
            text.append(this.lists[0].description());
            for (int index = 1; index < this.size; ++index) {
                text.append(" XOR ");
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

    private int scanNext(final DocId old) {
        int matching = 0;
        int index = 0;
        while (index < this.size) {
            if (!old.valid()
                || this.lists[index].docId().compareTo(old) <= 0) {
                this.advance(index);
                if (this.lists[index].atEnd()) {
                    this.erase(index);
                    continue;
                }
            }
            matching = this.count(index, matching);
            ++index;
        }
        return matching;
    }

    private int scanSkip(final DocId old, final DocId target) {
        int matching = 0;
        int index = 0;
        while (index < this.size) {
            if (!old.valid()
                || this.lists[index].docId().compareTo(target) < 0) {
                this.advanceTo(index, target);
                if (this.lists[index].atEnd()) {
                    this.erase(index);
                    continue;
                }
            }
            matching = this.count(index, matching);
            ++index;
        }
        return matching;
    }

    private void advance(final int index) {
        final PostList kid = this.lists[index];
        final PostList next = kid.next(0.0);
        if (!next.equals(kid)) {
            this.lists[index] = next;
            this.recalc.run();
        }
    }

    private void advanceTo(final int index, final DocId target) {
        final PostList kid = this.lists[index];
        final PostList next = kid.skipTo(target, 0.0);
        if (!next.equals(kid)) {
            this.lists[index] = next;
            this.recalc.run();
        }
    }

    private int count(final int index, final int matching) {
        final DocId found = this.lists[index].docId();
        int result = matching;
        if (!this.did.valid() || found.compareTo(this.did) < 0) {
            this.did = found;
            result = 1;
        } else if (found.equals(this.did)) {
            result = matching + 1;
        }
        return result;
    }

    private void erase(final int index) {
        --this.size;
        for (int position = index; position < this.size; ++position) {
            this.lists[position] = this.lists[position + 1];
        }
        this.recalc.run();
    }
}
