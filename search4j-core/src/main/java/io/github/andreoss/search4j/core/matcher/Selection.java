/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Partial ordering that places the element of a given rank in its sorted
 * position, with every element before it not after it and every element
 * after it not before it, exchanging elements in the same order as the
 * selection the reference library uses.
 *
 * @param <T> Element type
 * @since 0.10.0
 */
final class Selection<T> {

    /**
     * Elements partially ordered in place.
     */
    private final List<T> items;

    /**
     * Strict ordering of the elements.
     */
    private final BiPredicate<T, T> less;

    Selection(final List<T> items, final BiPredicate<T, T> less) {
        this.items = items;
        this.less = less;
    }

    void nth(final int first, final int nth, final int last) {
        if (first != last && nth != last) {
            this.introselect(
                first, nth, last, 2 * (31 - Integer.numberOfLeadingZeros(last - first))
            );
        }
    }

    private void introselect(final int start, final int nth, final int end, final int limit) {
        int first = start;
        int last = end;
        int depth = limit;
        boolean done = false;
        while (!done && last - first > 3) {
            if (depth == 0) {
                this.heapSelect(first, nth + 1, last);
                this.swap(first, nth);
                done = true;
            } else {
                depth = depth - 1;
                final int cut = this.partition(first, last);
                if (cut <= nth) {
                    first = cut;
                } else {
                    last = cut;
                }
            }
        }
        if (!done) {
            this.insertion(first, last);
        }
    }

    private int partition(final int first, final int last) {
        this.median(first, first + 1, first + (last - first) / 2, last - 1);
        int low = first + 1;
        int high = last;
        int result = -1;
        while (result < 0) {
            while (this.before(low, first)) {
                low = low + 1;
            }
            high = high - 1;
            while (this.before(first, high)) {
                high = high - 1;
            }
            if (low < high) {
                this.swap(low, high);
                low = low + 1;
            } else {
                result = low;
            }
        }
        return result;
    }

    private void median(final int result, final int one, final int two, final int three) {
        final int chosen;
        if (this.before(one, two)) {
            if (this.before(two, three)) {
                chosen = two;
            } else if (this.before(one, three)) {
                chosen = three;
            } else {
                chosen = one;
            }
        } else if (this.before(one, three)) {
            chosen = one;
        } else if (this.before(two, three)) {
            chosen = three;
        } else {
            chosen = two;
        }
        this.swap(result, chosen);
    }

    private void insertion(final int first, final int last) {
        for (int index = first + 1; index < last; ++index) {
            final T value = this.items.get(index);
            if (this.less.test(value, this.items.get(first))) {
                for (int hole = index; hole > first; --hole) {
                    this.items.set(hole, this.items.get(hole - 1));
                }
                this.items.set(first, value);
            } else {
                int hole = index;
                while (this.less.test(value, this.items.get(hole - 1))) {
                    this.items.set(hole, this.items.get(hole - 1));
                    hole = hole - 1;
                }
                this.items.set(hole, value);
            }
        }
    }

    private void heapSelect(final int first, final int middle, final int last) {
        this.makeHeap(first, middle);
        for (int index = middle; index < last; ++index) {
            if (this.before(index, first)) {
                final T value = this.items.get(index);
                this.items.set(index, this.items.get(first));
                this.adjust(first, 0, middle - first, value);
            }
        }
    }

    private void makeHeap(final int first, final int last) {
        final int len = last - first;
        if (len >= 2) {
            for (int parent = (len - 2) / 2; parent >= 0; --parent) {
                this.adjust(first, parent, len, this.items.get(first + parent));
            }
        }
    }

    private void adjust(final int first, final int start, final int len, final T value) {
        int hole = start;
        int second = start;
        while (second < (len - 1) / 2) {
            second = 2 * (second + 1);
            if (this.before(first + second, first + second - 1)) {
                second = second - 1;
            }
            this.items.set(first + hole, this.items.get(first + second));
            hole = second;
        }
        if ((len & 1) == 0 && second == (len - 2) / 2) {
            second = 2 * (second + 1);
            this.items.set(first + hole, this.items.get(first + second - 1));
            hole = second - 1;
        }
        int parent = (hole - 1) / 2;
        while (hole > start && this.less.test(this.items.get(first + parent), value)) {
            this.items.set(first + hole, this.items.get(first + parent));
            hole = parent;
            parent = (hole - 1) / 2;
        }
        this.items.set(first + hole, value);
    }

    private boolean before(final int left, final int right) {
        return this.less.test(this.items.get(left), this.items.get(right));
    }

    private void swap(final int left, final int right) {
        final T value = this.items.get(left);
        this.items.set(left, this.items.get(right));
        this.items.set(right, value);
    }
}
