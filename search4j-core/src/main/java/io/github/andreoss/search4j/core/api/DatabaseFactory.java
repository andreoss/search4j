/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for opening databases.
 *
 * <p>The concrete backend is chosen from the action bits of the opening
 * flags; a backend implementation is selected in a later layer.</p>
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
     * Opens a writable database at a path.
     *
     * @param path The database path
     * @param flags The opening flags
     * @return The writable database
     */
    public static WritableDatabase openWritable(
        final Path path,
        final int flags
    ) {
        Objects.requireNonNull(path, "path");
        throw new UnsupportedOperationException("Backend not yet provided");
    }

    /**
     * Opens a read-only database at a path.
     *
     * @param path The database path
     * @return The database
     */
    public static Database open(final Path path) {
        Objects.requireNonNull(path, "path");
        throw new UnsupportedOperationException("Backend not yet provided");
    }
}
