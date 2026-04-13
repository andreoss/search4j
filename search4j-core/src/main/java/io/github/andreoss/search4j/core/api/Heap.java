/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.util.Comparator;
import java.util.List;

/**
 * Binary heap laid out in a list whose top is the entry every other entry
 * ranks better than; entries may change rank after insertion, so the layout
 * is kept exactly as the sifts leave it.
 *
 * @param <T> Type of the entries
 * @since 0.5.4
 */
final class Heap<T> {

    /**
     * Entries in heap layout.
     */
    private final List<T> entries;

    /**
     * Rank of the entries, negative when the first ranks better.
     */
    private final Comparator<T> rank;

    /**
     * Creates a heap over a list.
     *
     * @param entries Entries, arranged in place
     * @param rank Rank of the entries, negative when the first ranks better
     */
    Heap(final List<T> entries, final Comparator<T> rank) {
        this.entries = entries;
        this.rank = rank;
    }

    /**
     * Arranges every entry into heap layout.
     */
    void make() {
        final int size = this.entries.size();
        if (size > 1) {
            for (int start = (size - 2) / 2; start >= 0; start = start - 1) {
                this.sift(start);
            }
        }
    }

    /**
     * Restores the layout after the top entry changed.
     */
    void replace() {
        this.sift(0);
    }

    /**
     * Restores the layout after an entry changed.
     *
     * @param entry Entry that changed
     */
    void siftdown(final T entry) {
        this.sift(this.entries.indexOf(entry));
    }

    private void sift(final int from) {
        final int size = this.entries.size();
        if (size >= 2 && (size - 2) / 2 >= from) {
            final int child = this.larger(2 * from + 1);
            if (!this.better(this.entries.get(child), this.entries.get(from))) {
                this.descend(from, child);
            }
        }
    }

    private void descend(final int from, final int first) {
        final int last = (this.entries.size() - 2) / 2;
        final T top = this.entries.get(from);
        int start = from;
        int child = first;
        while (child >= 0) {
            this.entries.set(start, this.entries.get(child));
            start = child;
            int next = -1;
            if (last >= child) {
                next = this.larger(2 * child + 1);
            }
            if (next >= 0 && this.better(this.entries.get(next), top)) {
                next = -1;
            }
            child = next;
        }
        this.entries.set(start, top);
    }

    private int larger(final int left) {
        int result = left;
        if (left + 1 < this.entries.size()
            && this.better(this.entries.get(left), this.entries.get(left + 1))) {
            result = left + 1;
        }
        return result;
    }

    private boolean better(final T left, final T right) {
        return this.rank.compare(left, right) < 0;
    }
}
