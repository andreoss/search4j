/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Brute-force positional model used by matcher tests.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Evaluates ordered, exact and unordered positional matches independently.
 *
 * @since 0.1.0
 */
final class PositionalOracle {

    private PositionalOracle() {
    }

    static List<DocId> exact(
        final List<Map<String, List<Integer>>> documents,
        final List<String> words
    ) {
        return PositionalOracle.expected(
            documents, words, Integer.MAX_VALUE, PositionalOracle::exactMatch
        );
    }

    static List<DocId> phrase(
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int window
    ) {
        return PositionalOracle.expected(
            documents, words, window, PositionalOracle::phraseMatch
        );
    }

    static List<DocId> near(
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int window
    ) {
        return PositionalOracle.expected(
            documents, words, window, PositionalOracle::nearMatch
        );
    }

    private static List<DocId> expected(
        final List<Map<String, List<Integer>>> documents,
        final List<String> words,
        final int window,
        final BiPredicate<List<List<Integer>>, Integer> matcher
    ) {
        final List<DocId> result = new ArrayList<>(0);
        for (int index = 0; index < documents.size(); ++index) {
            final List<List<Integer>> positions = PositionalOracle.positions(
                documents.get(index), words
            );
            if (matcher.test(positions, window)) {
                result.add(DocId.of(index + 1));
            }
        }
        return result;
    }

    private static List<List<Integer>> positions(
        final Map<String, List<Integer>> document,
        final List<String> words
    ) {
        final List<List<Integer>> result = new ArrayList<>(words.size());
        for (final String word : words) {
            result.add(document.getOrDefault(word, List.of()));
        }
        return result;
    }

    private static boolean exactMatch(
        final List<List<Integer>> positions, final int window
    ) {
        return PositionalOracle.sequence(
            positions, 0, -1, -1, window, true
        );
    }

    private static boolean phraseMatch(
        final List<List<Integer>> positions, final int window
    ) {
        return PositionalOracle.sequence(
            positions, 0, -1, -1, window, false
        );
    }

    private static boolean sequence(
        final List<List<Integer>> positions,
        final int index,
        final int previous,
        final int first,
        final int window,
        final boolean exact
    ) {
        boolean matched = index == positions.size();
        if (!matched) {
            matched = PositionalOracle.sequenceChoices(
                positions, index, previous, first, window, exact
            );
        }
        return matched;
    }

    private static boolean sequenceChoices(
        final List<List<Integer>> positions,
        final int index,
        final int previous,
        final int first,
        final int window,
        final boolean exact
    ) {
        boolean matched = false;
        for (final int position : positions.get(index)) {
            if (PositionalOracle.sequencePosition(
                positions, index, previous, first, window, exact, position
            )) {
                matched = true;
                break;
            }
        }
        return matched;
    }

    private static boolean sequencePosition(
        final List<List<Integer>> positions,
        final int index,
        final int previous,
        final int first,
        final int window,
        final boolean exact,
        final int position
    ) {
        boolean matched = PositionalOracle.afterPrevious(position, previous)
            && PositionalOracle.exactStep(position, previous, index, exact)
            && PositionalOracle.withinWindow(position, first, index, window);
        if (matched) {
            matched = PositionalOracle.continueSequence(
                positions, index, position, first, window, exact
            );
        }
        return matched;
    }

    private static boolean continueSequence(
        final List<List<Integer>> positions,
        final int index,
        final int position,
        final int first,
        final int window,
        final boolean exact
    ) {
        final boolean matched;
        if (index == 0) {
            matched = PositionalOracle.sequence(
                positions, index + 1, position, position, window, exact
            );
        } else {
            matched = PositionalOracle.sequence(
                positions, index + 1, position, first, window, exact
            );
        }
        return matched;
    }

    private static boolean afterPrevious(
        final int position, final int previous
    ) {
        return position > previous;
    }

    private static boolean exactStep(
        final int position,
        final int previous,
        final int index,
        final boolean exact
    ) {
        boolean result = true;
        if (exact && index > 0) {
            result = position == previous + 1;
        }
        return result;
    }

    private static boolean withinWindow(
        final int position,
        final int first,
        final int index,
        final int window
    ) {
        boolean result = true;
        if (index > 0) {
            result = position - first + 1 <= window;
        }
        return result;
    }

    private static boolean nearMatch(
        final List<List<Integer>> positions, final int window
    ) {
        return PositionalOracle.nearChoice(
            positions, 0, new HashSet<>(0), Integer.MAX_VALUE,
            Integer.MIN_VALUE, window
        );
    }

    private static boolean nearChoice(
        final List<List<Integer>> positions,
        final int index,
        final Set<Integer> chosen,
        final int minimum,
        final int maximum,
        final int window
    ) {
        final boolean matched;
        if (index == positions.size()) {
            matched = maximum - minimum < window;
        } else {
            matched = PositionalOracle.nearChoices(
                positions, index, chosen, minimum, maximum, window
            );
        }
        return matched;
    }

    private static boolean nearChoices(
        final List<List<Integer>> positions,
        final int index,
        final Set<Integer> chosen,
        final int minimum,
        final int maximum,
        final int window
    ) {
        boolean matched = false;
        for (final int position : positions.get(index)) {
            if (PositionalOracle.nearPosition(
                positions, index, chosen, minimum, maximum, window, position
            )) {
                matched = true;
                break;
            }
        }
        return matched;
    }

    private static boolean nearPosition(
        final List<List<Integer>> positions,
        final int index,
        final Set<Integer> chosen,
        final int minimum,
        final int maximum,
        final int window,
        final int position
    ) {
        boolean matched = false;
        if (!chosen.contains(position)) {
            chosen.add(position);
            matched = PositionalOracle.nearChoice(
                positions, index + 1, chosen,
                Math.min(minimum, position), Math.max(maximum, position),
                window
            );
            chosen.remove(position);
        }
        return matched;
    }
}
