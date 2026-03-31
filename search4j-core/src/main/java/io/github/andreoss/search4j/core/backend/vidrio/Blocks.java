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
 * Source of the blocks of one table.
 *
 * @since 0.1.0
 */
public interface Blocks extends AutoCloseable {

    /**
     * Block size of the source.
     *
     * @return Size of every block
     */
    BlockSize size();

    /**
     * Reads a block.
     *
     * @param number Block number
     * @return Block of {@link #size()} bytes
     */
    Block read(BlockNumber number);

    @Override
    void close();
}
