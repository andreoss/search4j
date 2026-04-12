/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Collapse bookkeeping of one match run: matches sharing the key of the
 * previous match form a group, and a group admits a limited number.
 *
 * @since 0.1.0
 */
final class MatchState {

    /**
     * Collapse key of the previous match, empty before the first.
     */
    private String prev;

    /**
     * Size of the current collapse group.
     */
    private int group;

    /**
     * Creates the state of a run.
     */
    MatchState() {
        this.prev = "";
        this.group = 0;
    }

    /**
     * Whether a match with a collapse key is admitted.
     *
     * @param key Collapse key of the match, empty or null for none
     * @param max Maximum size of a group
     * @return True when the key is empty, new, or its group has room
     */
    boolean admit(final String key, final int max) {
        final boolean include;
        if (key == null || key.isEmpty()) {
            include = true;
        } else if (key.equals(this.prev)) {
            this.group = this.group + 1;
            include = this.group < max;
        } else {
            this.prev = key;
            this.group = 1;
            include = true;
        }
        return include;
    }
}
