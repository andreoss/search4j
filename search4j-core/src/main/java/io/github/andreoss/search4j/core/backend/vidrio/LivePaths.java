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
 * Paths into a table being written, from the root as changed so far.
 *
 * @since 0.9.1
 */
final class LivePaths implements CursorPaths {

    /**
     * Blocks of the table as changed so far.
     */
    private final WriterBlocks blocks;

    /**
     * Path being changed.
     */
    private final Spine spine;

    /**
     * Number of changes so far.
     */
    private long changes;

    /**
     * Creates the source.
     *
     * @param store Store of the blocks
     * @param spine Path being changed
     */
    LivePaths(final BlockStore store, final Spine spine) {
        this.blocks = new WriterBlocks(store, spine);
        this.spine = spine;
    }

    @Override
    public long version() {
        return this.changes;
    }

    @Override
    public TreePath path() {
        return new TreePath(this.blocks, this.spine.root());
    }

    /**
     * Records a change of the table, so that open cursors rebuild their
     * paths.
     */
    void changed() {
        this.changes += 1L;
    }
}
