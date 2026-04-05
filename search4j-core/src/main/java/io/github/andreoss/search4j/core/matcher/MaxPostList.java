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
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;

/**
 * Union of several lists whose document weight is the largest child weight.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.NullAssignment",
    "PMD.ProhibitPublicStaticMethods"
})
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
     * Current document, absent before the first move and after the end.
     */
    private DocId current;

    /**
     * Creates a weighted maximum union.
     *
     * @param children Child lists, at least one
     * @param recalculation Callback run when a child is replaced
     */
    public MaxPostList(
        final List<? extends PostList> children,
        final Runnable recalculation
    ) {
        this.children = new ArrayList<>(children);
        this.recalc = recalculation;
        this.current = null;
    }

    /**
     * Creates a weighted maximum union.
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
        if (this.current == null) {
            throw new InvalidOperationError(
                "A maximum union is not on a document"
            );
        }
        return this.current;
    }

    @Override
    public boolean atEnd() {
        return this.children.isEmpty();
    }

    @Override
    public PostList next(final double least) {
        final DocId previous = this.current;
        this.current = null;
        int index = 0;
        while (index < this.children.size()) {
            final PostList child = this.children.get(index);
            final PostList moved = this.move(child, previous, least);
            if (moved != null) {
                this.observe(moved);
                index = index + 1;
            }
        }
        return this.finish();
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.current = null;
        int index = 0;
        while (index < this.children.size()) {
            final PostList child = this.children.get(index);
            final PostList moved = this.skip(child, target, least);
            if (moved != null) {
                this.observe(moved);
                index = index + 1;
            }
        }
        return this.finish();
    }

    @Override
    public TermCount termFrequency() {
        TermCount total = TermCount.ZERO;
        for (final PostList child : this.children) {
            total = total.plus(child.termFrequency());
        }
        return total;
    }

    @Override
    public TermCount wdf() {
        TermCount total = TermCount.ZERO;
        for (final PostList child : this.children) {
            if (this.on(child)) {
                total = total.plus(child.wdf());
            }
        }
        return total;
    }

    @Override
    public double weight(final DocStats doc) {
        double result = 0.0;
        for (final PostList child : this.children) {
            if (this.on(child)) {
                result = Math.max(result, child.weight(doc));
            }
        }
        return result;
    }

    @Override
    public double recalcMaxWeight() {
        double result = 0.0;
        for (final PostList child : this.children) {
            result = Math.max(result, child.recalcMaxWeight());
        }
        return result;
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return TermCount.ZERO;
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        DocRange range = whole;
        for (final PostList child : this.children) {
            final DocRange inner = child.docIdRange(whole);
            final DocId first;
            if (inner.first().compareTo(range.first()) < 0) {
                first = inner.first();
            } else {
                first = range.first();
            }
            final DocId last;
            if (inner.last().compareTo(range.last()) > 0) {
                last = inner.last();
            } else {
                last = range.last();
            }
            range = new DocRange(first, last);
        }
        return range;
    }

    @Override
    public String description() {
        final StringBuilder text = new StringBuilder("(");
        int index = 0;
        while (index < this.children.size()) {
            if (index > 0) {
                text.append(" MAX ");
            }
            text.append(this.children.get(index).description());
            index = index + 1;
        }
        return text.append(')').toString();
    }

    private boolean on(final PostList child) {
        return !child.atEnd() && child.docId().equals(this.current);
    }

    private PostList move(
        final PostList child, final DocId previous, final double least
    ) {
        final DocId here = MaxPostList.here(child);
        PostList moved = child;
        if (previous == null || here.compareTo(previous) <= 0) {
            if (previous == null || here.equals(previous)) {
                moved = child.next(least);
            } else {
                moved = child.skipTo(previous.next(), least);
            }
            moved = this.replace(child, moved);
        }
        return moved;
    }

    private PostList skip(
        final PostList child, final DocId target, final double least
    ) {
        final DocId here = MaxPostList.here(child);
        PostList moved = child;
        if (here.compareTo(target) < 0) {
            moved = this.replace(child, child.skipTo(target, least));
        }
        return moved;
    }

    private static DocId here(final PostList child) {
        final DocId result;
        if (child.atEnd()) {
            result = DocId.NONE;
        } else {
            result = child.docId();
        }
        return result;
    }

    private PostList replace(final PostList child, final PostList moved) {
        PostList result = moved;
        final int index = this.children.indexOf(child);
        this.children.set(index, moved);
        if (moved.atEnd()) {
            this.children.remove(index);
            result = null;
        } else if (!moved.equals(child)) {
            this.recalc.run();
        }
        return result;
    }

    private void observe(final PostList child) {
        final DocId candidate = child.docId();
        if (this.current == null
            || candidate.compareTo(this.current) < 0) {
            this.current = candidate;
        }
    }

    private PostList finish() {
        PostList result = this;
        if (this.children.isEmpty()) {
            result = EmptyPostList.INSTANCE;
        }
        return result;
    }
}
