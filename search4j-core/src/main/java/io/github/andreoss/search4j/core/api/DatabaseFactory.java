/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.ShardDatabase;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for opening databases and creating documents.
 *
 * <p>An in-memory database needs no path. Opening by path is rejected until
 * an on-disk backend is provided.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class DatabaseFactory {

    /**
     * Utility class, no instances.
     */
    private DatabaseFactory() {
    }

    /**
     * Creates an empty writable database held in memory.
     *
     * @return New database, independent of any other
     */
    public static WritableDatabase inMemory() {
        return new ShardDatabase(new InMemoryShard());
    }

    /**
     * Creates an empty document belonging to no database.
     *
     * @return New editable document
     */
    public static Document document() {
        return new DocumentInternal();
    }

    /**
     * Opens a writable database at a path; always unsupported for now.
     *
     * @param path Database directory, not null
     * @param flags Bitwise-or of action, backend and opening flags
     * @return Never returns normally
     */
    public static WritableDatabase openWritable(
        final Path path,
        final int flags
    ) {
        Objects.requireNonNull(path, "path");
        throw new UnsupportedOperationException("Backend not yet provided");
    }

    /**
     * Opens a read-only database at a path; always unsupported for now.
     *
     * @param path Database directory, not null
     * @return Never returns normally
     */
    public static Database open(final Path path) {
        Objects.requireNonNull(path, "path");
        throw new UnsupportedOperationException("Backend not yet provided");
    }
}
