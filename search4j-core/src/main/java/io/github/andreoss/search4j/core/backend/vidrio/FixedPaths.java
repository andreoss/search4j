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
 * Paths into a tree that does not change.
 *
 * @since 0.9.1
 */
final class FixedPaths implements CursorPaths {

    /**
     * Source of the blocks.
     */
    private final Blocks blocks;

    /**
     * Root block, at the top level of the tree.
     */
    private final Block top;

    /**
     * Creates the source.
     *
     * @param blocks Source of the blocks
     * @param top Root block, at the top level of the tree
     */
    FixedPaths(final Blocks blocks, final Block top) {
        this.blocks = blocks;
        this.top = top;
    }

    @Override
    public long version() {
        return 0L;
    }

    @Override
    public TreePath path() {
        return new TreePath(this.blocks, this.top);
    }
}
