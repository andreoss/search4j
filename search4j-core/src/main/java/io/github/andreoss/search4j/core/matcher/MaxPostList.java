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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;

/**
 * Union of several lists whose document weight is the largest child weight.
 *
 * <p>The union counts as one matching subquery, estimates its frequency
 * assuming independent children and gives way to its last child left.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class MaxPostList implements PostList {

    /**
     * Child lists, in order; exhausted children are dropped.
     */
    private final List<PostList> children;

    /**
     * Callback run when a child is replaced.
     */
    private final Runnable recalc;

    /**
     * Estimated number of documents matched.
     */
    private final TermCount frequency;

    /**
     * Current document, zero before the first move and after the end.
     */
    private int current;

    /**
     * Creates a weighted maximum union over the largest database.
     *
     * @param children Child lists, at least one
     * @param recalculation Callback run when a child is replaced
     */
    public MaxPostList(
        final List<? extends PostList> children,
        final Runnable recalculation
    ) {
        this(children, recalculation, TermCount.MAX);
    }

    MaxPostList(
        final List<? extends PostList> children,
        final Runnable recalculation, final TermCount dbsize
    ) {
        this(
            MaxPostList.estimate(children, dbsize), new ArrayList<>(children),
            recalculation
        );
    }

    private MaxPostList(
        final TermCount frequency, final List<PostList> children,
        final Runnable recalculation
    ) {
        this.children = children;
        this.recalc = recalculation;
        this.frequency = frequency;
    }

    /**
     * Creates a weighted maximum union over the largest database.
     *
     * @param children Child lists, at least one
     * @param recalculation Callback run when a child is replaced
     * @return Weighted maximum union
     */
    public static MaxPostList of(
        final List<? extends PostList> children,
        final Runnable recalculation
    ) {
        if (children == null) {
            throw new InvalidArgumentError("Children are required");
        }
        if (recalculation == null) {
            throw new InvalidArgumentError("A callback is required");
        }
        if (children.isEmpty()) {
            throw new InvalidArgumentError(
                "A maximum union needs at least one child"
            );
        }
        return new MaxPostList(children, recalculation);
    }

    @Override
    public DocId docId() {
        return new DocId(this.current);
    }

    @Override
    public boolean atEnd() {
        return this.current == 0;
    }

    @Override
    public PostList next(final double least) {
        final int old = this.current;
        this.current = 0;
        int index = 0;
        while (index < this.children.size()) {
            final PostList child = this.children.get(index);
            int here = 0;
            if (old != 0) {
                here = child.docId().value();
            }
            boolean kept = true;
            if (Integer.compareUnsigned(here, old) <= 0) {
                final PostList moved = MaxPostList.advance(child, old, here, least);
                kept = this.settle(index, child, moved);
                here = MaxPostList.position(moved, kept);
            }
            if (kept) {
                this.observe(here);
                index = index + 1;
            }
        }
        return this.finish();
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final int old = this.current;
        this.current = 0;
        int index = 0;
        while (index < this.children.size()) {
            final PostList child = this.children.get(index);
            int here = 0;
            if (old != 0) {
                here = child.docId().value();
            }
            boolean kept = true;
            if (Integer.compareUnsigned(here, target.value()) < 0) {
                final PostList moved = child.skipTo(target, least);
                kept = this.settle(index, child, moved);
                here = MaxPostList.position(moved, kept);
            }
            if (kept) {
                this.observe(here);
                index = index + 1;
            }
        }
        return this.finish();
    }

    @Override
    public TermCount termFrequency() {
        return this.frequency;
    }

    @Override
    public TermCount wdf() {
        long total = 0L;
        for (final PostList child : this.children) {
            if (child.docId().value() == this.current) {
                total = total + child.wdf().value();
            }
        }
        return TermCount.wrap(total);
    }

    @Override
    public double weight(final DocStats doc) {
        double result = 0.0;
        for (final PostList child : this.children) {
            if (child.docId().value() == this.current) {
                result = Math.max(result, child.weight(doc));
            }
        }
        return result;
    }

    @Override
    public double recalcMaxWeight() {
        double result = this.children.get(0).recalcMaxWeight();
        for (int index = 1; index < this.children.size(); ++index) {
            result = Math.max(result, this.children.get(index).recalcMaxWeight());
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return TermCount.ONE;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        DocRange range = this.children.get(0).docIdRange(whole);
        for (int index = 1; index < this.children.size(); ++index) {
            final DocRange inner = this.children.get(index).docIdRange(PostListMath.WHOLE);
            DocId first = range.first();
            if (inner.first().compareTo(first) < 0) {
                first = inner.first();
            }
            DocId last = range.last();
            if (inner.last().compareTo(last) > 0) {
                last = inner.last();
            }
            range = new DocRange(first, last);
        }
        return range;
    }

    @Override
    public String description() {
        final StringBuilder text = new StringBuilder("(");
        for (int index = 0; index < this.children.size(); ++index) {
            if (index > 0) {
                text.append(" MAX ");
            }
            text.append(this.children.get(index).description());
        }
        return text.append(')').toString();
    }

    private boolean settle(final int index, final PostList child, final PostList moved) {
        this.children.set(index, moved);
        boolean kept = true;
        if (moved.atEnd()) {
            this.children.remove(index);
            kept = false;
        } else if (!moved.equals(child)) {
            this.recalc.run();
        }
        return kept;
    }

    private static PostList advance(
        final PostList child, final int old, final int here, final double least
    ) {
        final PostList moved;
        if (old == 0 || here == old) {
            moved = child.next(least);
        } else {
            moved = child.skipTo(new DocId(old + 1), least);
        }
        return moved;
    }

    private static int position(final PostList list, final boolean kept) {
        int result = 0;
        if (kept) {
            result = list.docId().value();
        }
        return result;
    }

    private void observe(final int here) {
        if (this.current == 0 || Integer.compareUnsigned(here, this.current) < 0) {
            this.current = here;
        }
    }

    private PostList finish() {
        PostList result = this;
        if (this.children.size() == 1) {
            result = this.children.get(0);
        }
        return result;
    }

    private static TermCount estimate(
        final List<? extends PostList> lists, final TermCount dbsize
    ) {
        TermCount result = TermCount.ZERO;
        if (!lists.isEmpty()) {
            final double size = dbsize.value();
            final double scale = 1.0 / size;
            double prob = lists.get(0).termFrequency().value() * scale;
            for (int index = 1; index < lists.size(); ++index) {
                final double part = lists.get(index).termFrequency().value() * scale;
                prob += part - prob * part;
            }
            result = TermCount.wrap((long) (prob * size + 0.5));
        }
        return result;
    }
}
