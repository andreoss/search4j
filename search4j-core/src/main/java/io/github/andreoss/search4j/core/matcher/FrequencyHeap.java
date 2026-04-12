/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PostList;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a tree of binary disjunctions the way a Huffman code is built: the
 * two lists with the lowest term frequencies are joined first, the more
 * frequent on the left, using a binary heap whose tie order is fixed.
 *
 * @since 0.4.0
 */
final class FrequencyHeap {

    /**
     * Heap of lists, the least frequent at the top.
     */
    private final List<PostList> items;

    /**
     * Creates a heap over lists in the order they were gathered.
     *
     * @param lists Lists to join, at least two
     */
    FrequencyHeap(final List<PostList> lists) {
        this.items = new ArrayList<>(lists);
    }

    /**
     * Joins every list into one tree.
     *
     * @param recalc Receiver told when the maximum weight changes
     * @return Root of the tree
     */
    PostList tree(final Runnable recalc) {
        this.make();
        PostList merged;
        do {
            final PostList right = this.items.get(0);
            this.pop();
            this.items.remove(this.items.size() - 1);
            merged = new OrPostList(this.items.get(0), right, recalc);
            if (this.items.size() > 1) {
                this.items.set(0, merged);
                this.down(this.items.size(), 0);
            }
        } while (this.items.size() > 1);
        return merged;
    }

    private void make() {
        final int size = this.items.size();
        for (int start = (size - 2) / 2; size > 1 && start >= 0; start = start - 1) {
            this.down(size, start);
        }
    }

    private void pop() {
        final int size = this.items.size();
        if (size > 1) {
            final PostList top = this.items.get(0);
            this.items.set(0, this.items.get(size - 1));
            this.items.set(size - 1, top);
            this.down(size - 1, 0);
        }
    }

    private void down(final int size, final int from) {
        if (size >= 2 && (size - 2) / 2 >= from) {
            final int child = this.larger(size, 2 * from + 1);
            if (!this.above(child, from)) {
                this.descend(size, from, child);
            }
        }
    }

    private void descend(final int size, final int from, final int first) {
        final PostList top = this.items.get(from);
        int start = from;
        int child = first;
        boolean more = true;
        while (more) {
            this.items.set(start, this.items.get(child));
            start = child;
            if ((size - 2) / 2 < child) {
                more = false;
            } else {
                child = this.larger(size, 2 * child + 1);
                more = !FrequencyHeap.before(this.items.get(child), top);
            }
        }
        this.items.set(start, top);
    }

    private int larger(final int size, final int child) {
        int result = child;
        if (child + 1 < size && this.above(child, child + 1)) {
            result = child + 1;
        }
        return result;
    }

    private boolean above(final int left, final int right) {
        return FrequencyHeap.before(this.items.get(left), this.items.get(right));
    }

    private static boolean before(final PostList left, final PostList right) {
        return Integer.toUnsignedLong(left.termFrequency().value())
            > Integer.toUnsignedLong(right.termFrequency().value());
    }
}
