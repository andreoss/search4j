/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.Objects;

/**
 * Where a table's B-tree starts and which revision of it is read.
 *
 * @param block Number of the root block
 * @param level Height of the tree, 0 when the root is a leaf
 * @param size Block size of the table file
 * @param revision Revision being read; no block may be newer
 * @param fake Whether the root is an empty leaf not stored on disk
 * @since 0.1.0
 */
public record TableRoot(
    BlockNumber block, int level, BlockSize size, RevisionNumber revision,
    boolean fake
) {

    /**
     * Creates a root.
     *
     * @param block Number of the root block
     * @param level Level below {@link VidrioFormat#CURSOR_LEVELS}
     * @param size Block size of the table file
     * @param revision Revision being read
     * @param fake Whether the root is an empty leaf, then at level 0
     */
    public TableRoot {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(revision, "revision");
        if (level < 0 || level >= VidrioFormat.CURSOR_LEVELS
            || fake && level != 0) {
            throw new IllegalArgumentException(
                String.format("Bad table level: %d", level)
            );
        }
    }

    /**
     * Creates the root of an empty table whose root block is not on disk.
     *
     * @param size Block size of the table file
     * @param revision Revision being read
     * @return Root
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static TableRoot empty(
        final BlockSize size, final RevisionNumber revision
    ) {
        return new TableRoot(new BlockNumber(0L), 0, size, revision, true);
    }
}
