/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Unsigned position arithmetic, a binary heap of position lists and the
 * ordering of positional children shared by the positional filters.
 *
 * @since 0.10.0
 */
final class Positions {

    /**
     * Mask of an unsigned 32-bit position.
     */
    static final long MASK = 0xFFFF_FFFFL;

    /**
     * Utility class, no instances.
     */
    private Positions() {
    }

    static long at(final PositionList list) {
        return list.position().value() & Positions.MASK;
    }

    static long at(final PositionList list, final boolean live) {
        long result = 0L;
        if (live) {
            result = Positions.at(list);
        }
        return result;
    }

    static long flag(final boolean live) {
        long result = 1L;
        if (live) {
            result = 0L;
        }
        return result;
    }

    static boolean skip(final PositionList list, final long target) {
        final long wrapped = target & Positions.MASK;
        boolean result = false;
        if (wrapped <= Integer.MAX_VALUE) {
            result = list.skipTo(new TermPos((int) wrapped));
        }
        return result;
    }

    static void sortByWdf(final List<PostList> terms) {
        for (int index = 1; index < terms.size(); ++index) {
            final PostList item = terms.get(index);
            int hole = index;
            while (hole > 0
                && item.wdf().compareTo(terms.get(hole - 1).wdf()) < 0) {
                terms.set(hole, terms.get(hole - 1));
                hole = hole - 1;
            }
            terms.set(hole, item);
        }
    }

    static <T> void push(final T[] items, final int size, final BiPredicate<T, T> less) {
        int hole = size - 1;
        if (hole > 0) {
            final T top = items[hole];
            int parent = (hole - 1) / 2;
            while (hole > 0 && less.test(items[parent], top)) {
                items[hole] = items[parent];
                hole = parent;
                parent = (hole - 1) / 2;
            }
            items[hole] = top;
        }
    }

    static <T> void pop(final T[] items, final int size, final BiPredicate<T, T> less) {
        if (size > 1) {
            final T top = items[0];
            items[0] = items[size - 1];
            items[size - 1] = top;
            Positions.down(items, size - 1, 0, less);
        }
    }

    static <T> void replace(final T[] items, final int size, final BiPredicate<T, T> less) {
        Positions.down(items, size, 0, less);
    }

    static <T> void make(final T[] items, final int size, final BiPredicate<T, T> less) {
        if (size > 1) {
            for (int start = (size - 2) / 2; start >= 0; --start) {
                Positions.down(items, size, start, less);
            }
        }
    }

    private static <T> void down(
        final T[] items, final int len, final int from, final BiPredicate<T, T> less
    ) {
        if (len >= 2 && (len - 2) / 2 >= from) {
            Positions.sink(items, len, from, less);
        }
    }

    private static <T> void sink(
        final T[] items, final int len, final int from, final BiPredicate<T, T> less
    ) {
        int child = Positions.larger(items, len, 2 * from + 1, less);
        if (!less.test(items[child], items[from])) {
            final T top = items[from];
            int start = from;
            boolean more = true;
            while (more) {
                items[start] = items[child];
                start = child;
                more = (len - 2) / 2 >= child;
                if (more) {
                    child = Positions.larger(items, len, 2 * child + 1, less);
                    more = !less.test(items[child], top);
                }
            }
            items[start] = top;
        }
    }

    private static <T> int larger(
        final T[] items, final int len, final int child, final BiPredicate<T, T> less
    ) {
        int result = child;
        if (child + 1 < len && less.test(items[child], items[child + 1])) {
            result = child + 1;
        }
        return result;
    }
}
