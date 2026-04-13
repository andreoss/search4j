/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.util.HashMap;
import java.util.Map;

/**
 * Collapsing of a match run: keeps at most a number of the best matches per
 * collapse key and counts what it leaves out.
 *
 * @since 0.5.4
 */
final class Collapser {

    /**
     * Settings shared by the groups.
     */
    private final Grouping grouping;

    /**
     * Groups by collapse key.
     */
    private final Map<String, CollapseGroup> table;

    /**
     * Counters: matches with a key admitted, matches without a key, matches
     * left out and matches considered.
     */
    private final int[] counts;

    /**
     * Group of the match being handled.
     */
    private CollapseGroup current;

    /**
     * Position the last replacing match took over.
     */
    private int evicted;

    /**
     * Creates the collapsing of a run.
     *
     * @param grouping Settings shared by the groups
     */
    Collapser(final Grouping grouping) {
        this(grouping, new HashMap<>(16), new int[4]);
    }

    /**
     * Creates the collapsing of a run with its groups and counters.
     *
     * @param grouping Settings shared by the groups
     * @param table Groups by collapse key, empty
     * @param counts Counters, zero
     */
    private Collapser(
        final Grouping grouping, final Map<String, CollapseGroup> table,
        final int... counts
    ) {
        this.grouping = grouping;
        this.table = table;
        this.counts = counts;
    }

    /**
     * Decides how a match is handled.
     *
     * @param item Match with its collapse key
     * @return Handling of the match
     */
    Collapse check(final Candidate item) {
        this.counts[3] = this.counts[3] + 1;
        final Collapse result;
        final String key = item.group();
        if (key.isEmpty()) {
            this.counts[1] = this.counts[1] + 1;
            result = Collapse.EMPTY;
        } else if (this.table.containsKey(key)) {
            this.current = this.table.get(key);
            result = this.current.check(
                this.grouping, item, index -> this.evicted = index
            );
            if (result == Collapse.ADD) {
                this.counts[0] = this.counts[0] + 1;
            } else {
                this.counts[2] = this.counts[2] + 1;
            }
        } else {
            this.current = new CollapseGroup(item.docId());
            this.table.put(key, this.current);
            this.counts[0] = this.counts[0] + 1;
            result = Collapse.NEW;
        }
        return result;
    }

    /**
     * Records a match once kept.
     *
     * @param action Handling decided for the match, new or add
     * @param index Position of the match in the kept matches
     */
    void process(final Collapse action, final int index) {
        if (action == Collapse.NEW) {
            this.current.place(index);
        } else {
            this.current.add(this.grouping, index);
        }
    }

    /**
     * Follows a kept match to a new position.
     *
     * @param from Old position
     * @param target New position, already holding the match
     */
    void moved(final int from, final int target) {
        final String key = this.grouping.slots().apply(target).group();
        if (!key.isEmpty()) {
            this.table.get(key).moved(from, target);
        }
    }

    int evicted() {
        return this.evicted;
    }

    int lower() {
        return this.counts[1] + this.counts[0];
    }

    int considered() {
        return this.counts[3];
    }

    int ignored() {
        return this.counts[2];
    }
}
