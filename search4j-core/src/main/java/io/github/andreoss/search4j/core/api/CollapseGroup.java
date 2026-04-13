/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Kept matches sharing one collapse key: positions in the kept matches with
 * the document each position held when recorded, arranged worst first once
 * the group overflows.
 *
 * @since 0.5.4
 */
final class CollapseGroup {

    /**
     * Recorded positions with their documents.
     */
    private final List<Kept> kept;

    /**
     * Number of matches with the key left out so far.
     */
    private int count;

    /**
     * Creates a group whose first match is not placed yet.
     *
     * @param docid Document of the first match
     */
    CollapseGroup(final DocId docid) {
        this(CollapseGroup.first(docid));
    }

    /**
     * Creates a group.
     *
     * @param kept Recorded positions with their documents
     */
    private CollapseGroup(final List<Kept> kept) {
        this.kept = kept;
    }

    /**
     * Decides how a match with the key is handled.
     *
     * @param grouping Settings of the run
     * @param item The match
     * @param evict Receives the position the match takes over
     * @return Handling of the match
     */
    Collapse check(
        final Grouping grouping, final Candidate item, final IntConsumer evict
    ) {
        Collapse result = Collapse.ADD;
        if (this.kept.size() >= grouping.max()) {
            result = this.overflow(grouping, item, evict);
        }
        return result;
    }

    /**
     * Places the first match of the group.
     *
     * @param index Position of the match in the kept matches
     */
    void place(final int index) {
        this.kept.set(0, new Kept(index, this.kept.get(0).docid()));
    }

    /**
     * Records a match the group had room for.
     *
     * @param grouping Settings of the run
     * @param index Position of the match in the kept matches
     */
    void add(final Grouping grouping, final int index) {
        final Kept entry = new Kept(index, grouping.slots().apply(index).docId());
        if (this.kept.size() < grouping.max()) {
            this.kept.add(entry);
        } else {
            this.kept.set(0, entry);
            CollapseGroup.heap(this.kept, grouping).replace();
        }
    }

    /**
     * Follows a kept match to a new position.
     *
     * @param from Old position
     * @param target New position
     */
    void moved(final int from, final int target) {
        for (int idx = 0; idx < this.kept.size(); idx = idx + 1) {
            final Kept entry = this.kept.get(idx);
            if (entry.index() == from) {
                this.kept.set(idx, new Kept(target, entry.docid()));
                break;
            }
        }
    }

    private Collapse overflow(
        final Grouping grouping, final Candidate item, final IntConsumer evict
    ) {
        final Heap<Kept> heap = CollapseGroup.heap(this.kept, grouping);
        if (this.count == 0 && grouping.max() != 1) {
            heap.make();
        }
        this.count = this.count + 1;
        final Kept front = this.kept.get(0);
        final Candidate old = grouping.slots().apply(front.index());
        final boolean present = front.docid().equals(old.docId());
        Collapse result = Collapse.REPLACE;
        if (present && grouping.order().compare(old, item) < 0) {
            result = Collapse.REJECT;
        } else if (present) {
            evict.accept(front.index());
            this.kept.set(0, new Kept(front.index(), item.docId()));
            heap.replace();
        }
        return result;
    }

    private static List<Kept> first(final DocId docid) {
        final List<Kept> result = new ArrayList<>(4);
        result.add(new Kept(0, docid));
        return result;
    }

    private static Heap<Kept> heap(final List<Kept> kept, final Grouping grouping) {
        return new Heap<>(kept, Comparator.comparing(Kept::index, grouping.ranks()));
    }

    /**
     * A recorded position with the document it held.
     *
     * @param index Position in the kept matches
     * @param docid Document at the position when recorded
     */
    private record Kept(int index, DocId docid) {
    }
}
