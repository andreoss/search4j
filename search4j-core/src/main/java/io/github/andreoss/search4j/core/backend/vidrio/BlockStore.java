/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

/**
 * Source of the blocks of one table that also takes written blocks.
 *
 * @since 0.1.0
 */
public interface BlockStore extends Blocks {

    /**
     * Writes a block in place.
     *
     * @param number Block number
     * @param data Block bytes, {@link #size()} long, not kept
     */
    void write(BlockNumber number, byte[] data);

    /**
     * Forces the blocks written so far to durable storage.
     */
    void sync();
}
