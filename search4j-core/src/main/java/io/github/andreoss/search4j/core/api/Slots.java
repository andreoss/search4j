/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Kept matches by position: the live list, and the matches left at
 * positions past its end when it shrank, still seen by collapse groups
 * recorded earlier.
 *
 * @since 0.5.4
 */
final class Slots implements IntFunction<Candidate> {

    /**
     * Kept matches.
     */
    private final List<Candidate> live;

    /**
     * Matches at positions past the end of the kept matches, by position.
     */
    private final Map<Integer, Candidate> dropped;

    /**
     * Creates the positions over a list of kept matches.
     *
     * @param live Kept matches
     */
    Slots(final List<Candidate> live) {
        this(live, new HashMap<>(8));
    }

    /**
     * Creates the positions.
     *
     * @param live Kept matches
     * @param dropped Matches past the end, by position
     */
    private Slots(final List<Candidate> live, final Map<Integer, Candidate> dropped) {
        this.live = live;
        this.dropped = dropped;
    }

    @Override
    public Candidate apply(final int index) {
        final Candidate result;
        if (index < this.live.size()) {
            result = this.live.get(index);
        } else {
            result = this.dropped.get(index);
        }
        return result;
    }

    /**
     * Places a match at a position, past the end when the position is.
     *
     * @param index Position
     * @param item Match
     * @return True when the position is within the kept matches
     */
    boolean put(final int index, final Candidate item) {
        final boolean inside = index < this.live.size();
        if (inside) {
            this.live.set(index, item);
        } else {
            this.dropped.put(index, item);
        }
        return inside;
    }

    /**
     * Shrinks the kept matches, leaving the matches past the new end at
     * their positions.
     *
     * @param size New number of kept matches
     */
    void truncate(final int size) {
        for (int idx = size; idx < this.live.size(); idx = idx + 1) {
            this.dropped.put(idx, this.live.get(idx));
        }
        this.live.subList(size, this.live.size()).clear();
    }
}
