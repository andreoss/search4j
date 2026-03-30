/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk table format definitions of the vidrio backend.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseError;
import io.github.andreoss.search4j.core.error.DatabaseModifiedError;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A B-tree table opened to read.
 *
 * <p>Lookups through the table share one cursor and are not thread-safe;
 * each {@link #cursor()} is independent.</p>
 *
 * @since 0.1.0
 */
public final class VidrioTable implements AutoCloseable {

    /**
     * Source of the blocks.
     */
    private final Blocks blocks;

    /**
     * Root of the tree.
     */
    private final TableRoot base;

    /**
     * Root block.
     */
    private final Block top;

    /**
     * Cursor of the lookups.
     */
    private final TableCursor lookup;

    /**
     * Creates a table over its parts.
     *
     * @param blocks Source of the blocks
     * @param base Root of the tree
     * @param top Root block
     */
    private VidrioTable(
        final Blocks blocks, final TableRoot base, final Block top
    ) {
        this.blocks = blocks;
        this.base = base;
        this.top = top;
        this.lookup = new TableCursor(blocks, top);
    }

    /**
     * Opens a table file to read.
     *
     * @param path Table file
     * @param root Root of the tree, with the file's block size
     * @return Table
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioTable open(final Path path, final TableRoot root) {
        return VidrioTable.over(new TableFile(path, root.size()), root);
    }

    /**
     * Opens a table over a block source, reading its root block.
     *
     * <p>The source is closed if the root cannot be read.</p>
     *
     * @param blocks Source of the blocks, of the root's block size
     * @param root Root of the tree
     * @return Table
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioTable over(final Blocks blocks, final TableRoot root) {
        if (!blocks.size().equals(root.size())) {
            blocks.close();
            throw new IllegalArgumentException(
                String.format("Block size mismatch: %s", root.size())
            );
        }
        try {
            return new VidrioTable(
                blocks, root, VidrioTable.rootOf(blocks, root)
            );
        } catch (final DatabaseError ex) {
            blocks.close();
            throw ex;
        }
    }

    /**
     * Root of the tree.
     *
     * @return Root
     */
    public TableRoot root() {
        return this.base;
    }

    /**
     * Creates an unpositioned cursor.
     *
     * @return Cursor
     */
    public TableCursor cursor() {
        return new TableCursor(this.blocks, this.top);
    }

    /**
     * Reads the tag of a key.
     *
     * @param key Key bytes; a key over {@link ItemKey#MAX_LENGTH} bytes is
     *  never present
     * @return Tag, reassembled and inflated, or empty when absent
     */
    public Optional<byte[]> get(final byte[] key) {
        final Optional<byte[]> tag;
        if (this.lookup.exact(key)) {
            tag = Optional.of(this.lookup.tag());
        } else {
            tag = Optional.empty();
        }
        return tag;
    }

    /**
     * Tests whether a key is present.
     *
     * @param key Key bytes
     * @return True if present
     */
    public boolean contains(final byte[] key) {
        return this.lookup.seek(key);
    }

    @Override
    public void close() {
        this.blocks.close();
    }

    private static Block rootOf(final Blocks blocks, final TableRoot root) {
        final Block block;
        if (root.fake()) {
            block = Block.fakeRoot(root.size());
        } else {
            block = blocks.read(root.block());
            if (block.revision().compareTo(root.revision()) > 0) {
                throw new DatabaseModifiedError(
                    "The revision being read has been discarded"
                );
            }
            if (block.level() != root.level()) {
                throw new DatabaseCorruptError(
                    String.format(
                        "Expected block %s to be level %d, not %d",
                        root.block(), root.level(), block.level()
                    )
                );
            }
        }
        return block;
    }
}
