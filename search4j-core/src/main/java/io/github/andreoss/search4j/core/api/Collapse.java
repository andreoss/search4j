/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Handling of a match by collapsing.
 *
 * @since 0.5.4
 */
enum Collapse {

    /**
     * The match has no collapse key and is never collapsed.
     */
    EMPTY,

    /**
     * The match is the first seen with its key.
     */
    NEW,

    /**
     * The group of the key has room for the match.
     */
    ADD,

    /**
     * The group of the key is full of better matches.
     */
    REJECT,

    /**
     * The match takes the place of a kept match with its key.
     */
    REPLACE
}
