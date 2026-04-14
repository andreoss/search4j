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
 * Blocks of a table being written, as changed so far: a block on the path
 * being changed is read from memory, any other from the store, so reading
 * writes nothing.
 *
 * @since 0.9.0
 */
final class WriterBlocks implements Blocks {

    /**
     * Store of the blocks.
     */
    private final BlockStore store;

    /**
     * Path being changed.
     */
    private final Spine spine;

    /**
     * Creates the source.
     *
     * @param store Store of the blocks
     * @param spine Path being changed
     */
    WriterBlocks(final BlockStore store, final Spine spine) {
        this.store = store;
        this.spine = spine;
    }

    @Override
    public BlockSize size() {
        return this.store.size();
    }

    @Override
    public Block read(final BlockNumber number) {
        return this.spine.held(number).orElseGet(() -> this.store.read(number));
    }

    @Override
    public void close() {
        this.store.close();
    }
}
