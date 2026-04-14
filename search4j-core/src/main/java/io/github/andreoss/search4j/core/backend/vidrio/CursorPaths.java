/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * Source of paths into a tree that may change while a cursor is open.
 *
 * <p>A cursor holding a path built at an earlier version builds a new one
 * before it moves again.</p>
 *
 * @since 0.9.1
 */
interface CursorPaths {

    /**
     * Version of the tree, changed by every change a cursor must see.
     *
     * @return Version number
     */
    long version();

    /**
     * Path from the root of the tree as it is now.
     *
     * @return Path holding the root only
     */
    TreePath path();
}
