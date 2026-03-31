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

import java.util.Objects;

/**
 * Position in the chain of free-list blocks.
 *
 * @param block Free-list block
 * @param offset Byte offset in the block, a multiple of four
 * @since 0.1.0
 */
public record FreeListMark(BlockNumber block, int offset) {

    /**
     * The position of a free list never started.
     */
    public static final FreeListMark START =
        new FreeListMark(new BlockNumber(0L), 0);

    /**
     * Creates a position.
     *
     * @param block Free-list block
     * @param offset Byte offset, a non-negative multiple of four
     */
    public FreeListMark {
        Objects.requireNonNull(block, "block");
        if (offset < 0 || offset % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                String.format("Bad free-list offset: %d", offset)
            );
        }
    }
}
