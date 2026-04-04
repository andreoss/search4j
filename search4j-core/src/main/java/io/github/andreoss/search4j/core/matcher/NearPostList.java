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
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Unordered positional match contained in a term-position window.
 *
 * @since 0.1.0
 */
public final class NearPostList extends SelectPostList {

    /**
     * Creates an unordered positional filter.
     *
     * @param candidates Stream containing every required term
     * @param span Maximum exclusive distance between selected positions
     * @param terms Terms in query order
     */
    public NearPostList(
        final PostList candidates,
        final TermPos span,
        final List<? extends PostList> terms
    ) {
        this(SelectSetup.positional(
            SelectSetup.simple(candidates, terms, 2), span
        ));
    }

    private NearPostList(final SelectSetup setup) {
        super(setup);
    }

    @Override
    public TermCount wdf() {
        return this.minimumWdf();
    }

    @Override
    public String description() {
        return String.format(
            "(Near %s %s)", this.window(), this.source().description()
        );
    }

    @Override
    boolean testDoc() {
        final List<List<Integer>> positions = new ArrayList<>(this.terms().size());
        final NavigableSet<Integer> starts = new TreeSet<>();
        boolean possible = this.window().value() > 0;
        for (final PostList term : this.terms()) {
            final PositionList list = term.readPositionList();
            final List<Integer> found = new ArrayList<>(0);
            while (list.next()) {
                final int position = list.position().value();
                found.add(position);
                starts.add(position);
            }
            if (found.isEmpty()) {
                possible = false;
            }
            positions.add(List.copyOf(found));
        }
        boolean matched = false;
        if (possible) {
            for (final int start : starts) {
                final long end = (long) start + this.window().value() - 1L;
                final List<Integer> order = NearPostList.order(
                    positions, start, end
                );
                if (order != null && NearPostList.assign(
                    positions, order, 0, new HashSet<>(0), start, end
                )) {
                    matched = true;
                    break;
                }
            }
        }
        return matched;
    }

    private static List<Integer> order(
        final List<List<Integer>> positions,
        final int start,
        final long end
    ) {
        final List<Integer> order = new ArrayList<>(positions.size());
        boolean possible = true;
        for (int index = 0; index < positions.size(); ++index) {
            if (NearPostList.count(positions.get(index), start, end) == 0) {
                possible = false;
            }
            order.add(index);
        }
        order.sort(
            Comparator.comparingInt(
                index -> NearPostList.count(positions.get(index), start, end)
            )
        );
        List<Integer> result = null;
        if (possible) {
            result = order;
        }
        return result;
    }

    private static int count(
        final List<Integer> positions, final int start, final long end
    ) {
        int count = 0;
        for (final int position : positions) {
            if (position > end) {
                break;
            }
            if (position >= start) {
                ++count;
            }
        }
        return count;
    }

    private static boolean assign(
        final List<List<Integer>> positions,
        final List<Integer> order,
        final int index,
        final Set<Integer> chosen,
        final long start,
        final long end
    ) {
        boolean assigned = index == order.size();
        if (!assigned) {
            assigned = NearPostList.assignChoices(
                positions.get(order.get(index)), positions, order,
                index, chosen, start, end
            );
        }
        return assigned;
    }

    private static boolean assignChoices(
        final List<Integer> choices,
        final List<List<Integer>> positions,
        final List<Integer> order,
        final int index,
        final Set<Integer> chosen,
        final long start,
        final long end
    ) {
        boolean assigned = false;
        int choice = 0;
        while (choice < choices.size() && !assigned) {
            final int position = choices.get(choice);
            if (position > end) {
                choice = choices.size();
            } else {
                assigned = NearPostList.assignChoice(
                    position, positions, order, index, chosen, start, end
                );
                ++choice;
            }
        }
        return assigned;
    }

    private static boolean assignChoice(
        final int position,
        final List<List<Integer>> positions,
        final List<Integer> order,
        final int index,
        final Set<Integer> chosen,
        final long start,
        final long end
    ) {
        boolean assigned = false;
        if (position >= start && chosen.add(position)) {
            assigned = NearPostList.assign(
                positions, order, index + 1, chosen, start, end
            );
            chosen.remove(position);
        }
        return assigned;
    }
}
