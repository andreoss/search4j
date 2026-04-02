/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

/**
 * Lexicographic ordering of sortable double encodings.
 *
 * @since 0.1.0
 */
final class SortableDoubleOrder {

    private SortableDoubleOrder() {
    }

    static boolean ordered(final double smaller, final double larger) {
        return SortableDoubleOrder.compare(
            SortableDoubleEncoder.encode(smaller),
            SortableDoubleEncoder.encode(larger)
        ) <= 0;
    }

    private static int compare(final byte[] left, final byte[] right) {
        int result = 0;
        final int length = Math.min(left.length, right.length);
        int index = 0;
        while (result == 0 && index < length) {
            result = (left[index] & 0xFF) - (right[index] & 0xFF);
            index += 1;
        }
        if (result == 0) {
            result = Integer.compare(left.length, right.length);
        }
        return result;
    }
}
