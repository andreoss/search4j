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
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The table files of a database directory and how each opens.
 *
 * <p>A table created on its first write is optional: when its file is
 * absent and its root is not on disk, it reads as empty.</p>
 *
 * @since 0.1.0
 */
public final class TableFiles {

    /**
     * Database directory.
     */
    private final Path dir;

    /**
     * Creates the files of a directory.
     *
     * @param dir Database directory
     */
    public TableFiles(final Path dir) {
        this.dir = dir;
    }

    /**
     * Database directory.
     *
     * @return Directory
     */
    public Path dir() {
        return this.dir;
    }

    /**
     * File of a table.
     *
     * @param type Table
     * @return Path inside the directory
     */
    public Path path(final TableType type) {
        return this.dir.resolve(type.fileName());
    }

    /**
     * Tests whether a table file exists.
     *
     * @param type Table
     * @return True when the file is present
     */
    public boolean exists(final TableType type) {
        return Files.isRegularFile(this.path(type));
    }

    /**
     * Opens a table to read.
     *
     * @param type Table
     * @param state Root information of the revision read
     * @return Table, empty when optional and absent
     */
    public VidrioTable read(final TableType type, final TableState state) {
        final VidrioTable table;
        if (this.exists(type) || !TableFiles.optional(type, state)) {
            table = VidrioTable.open(this.path(type), state.root());
        } else {
            table = VidrioTable.over(
                new EmptyBlocks(state.root().size()), state.root()
            );
        }
        return table;
    }

    /**
     * Creates a table file, replacing any present, and opens it to write.
     *
     * @param type Table
     * @param state Root information to start from, its root not on disk
     * @return Table writer
     */
    public TableWriter create(final TableType type, final TableState state) {
        return TableWriter.open(
            TableFile.create(this.path(type), state.root().size()), state
        );
    }

    /**
     * Opens an existing table file to write.
     *
     * @param type Table
     * @param state Root information of the revision continued from
     * @return Table writer
     */
    public TableWriter update(final TableType type, final TableState state) {
        return TableWriter.open(
            TableFile.update(this.path(type), state.root().size()), state
        );
    }

    /**
     * Removes a table file if present.
     *
     * @param type Table
     */
    public void remove(final TableType type) {
        try {
            Files.deleteIfExists(this.path(type));
        } catch (final IOException ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Couldn't remove %s", this.path(type)),
                ex.getMessage()
            );
            error.initCause(ex);
            throw error;
        }
    }

    /**
     * Raises the error of a database directory without tables.
     *
     * @return Error naming the directory
     */
    public DatabaseNotFoundError missing() {
        return new DatabaseNotFoundError(
            String.format("No vidrio database found at path '%s'", this.dir)
        );
    }

    private static boolean optional(
        final TableType type, final TableState state
    ) {
        return type.laziness() != TableLaziness.EAGER && state.root().fake();
    }
}
