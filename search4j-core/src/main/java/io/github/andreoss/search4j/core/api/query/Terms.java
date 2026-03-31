/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query trees of the public API: leaves, operators and their descriptions.
 */
package io.github.andreoss.search4j.core.api.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Term listings of a query.
 *
 * @since 0.1.0
 */
final class Terms {

    /**
     * Utility class, no instances.
     */
    private Terms() {
    }

    /**
     * Terms of sorted positioned terms, neighbours that compare equal kept
     * once.
     *
     * @param sorted Positioned terms in the order wanted
     * @param same Comparison deciding which neighbours repeat each other
     * @return Terms without the repetitions
     */
    static List<String> distinct(
        final Stream<TermAt> sorted, final Comparator<TermAt> same
    ) {
        final List<TermAt> terms = sorted.toList();
        final List<String> result = new ArrayList<>(terms.size());
        for (int index = 0; index < terms.size(); ++index) {
            if (
                index == 0
                    || same.compare(terms.get(index - 1), terms.get(index)) != 0
            ) {
                result.add(terms.get(index).term());
            }
        }
        return result;
    }
}
