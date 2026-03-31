/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;

/**
 * Blocks of a table whose file does not exist: an empty table whose root
 * is not on disk reads no block at all.
 *
 * @since 0.1.0
 */
final class EmptyBlocks implements Blocks {

    /**
     * Block size.
     */
    private final BlockSize bsize;

    /**
     * Creates the source.
     *
     * @param size Block size
     */
    EmptyBlocks(final BlockSize size) {
        this.bsize = size;
    }

    @Override
    public BlockSize size() {
        return this.bsize;
    }

    @Override
    public Block read(final BlockNumber number) {
        throw new DatabaseCorruptError(
            String.format("Block %s read from a table without a file", number)
        );
    }

    @Override
    public void close() {
        this.bsize.bytes();
    }
}
