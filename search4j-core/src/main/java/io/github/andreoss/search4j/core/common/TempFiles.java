/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Common utilities of the search engine.
 */
package io.github.andreoss.search4j.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Temporary files created under a caller-provided directory.
 *
 * <p>Files land under {@code scratch/} in the repository, never in the system
 * temporary directory, so that runs never write outside the repository.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class TempFiles {

    /**
     * Utility class, no instances.
     */
    private TempFiles() {
    }

    /**
     * Creates a new empty temporary file in the given directory.
     *
     * @param directory The scratch directory
     * @param prefix The file name prefix
     * @param suffix The file name suffix
     * @return The created path
     * @throws IOException When the file cannot be created
     */
    public static Path create(
        final Path directory,
        final String prefix,
        final String suffix
    ) throws IOException {
        Objects.requireNonNull(directory, "directory");
        return Files.createTempFile(directory, prefix, suffix);
    }

    /**
     * Deletes a temporary file, ignoring absence.
     *
     * @param path The path to delete
     * @throws IOException When the file cannot be removed
     */
    public static void delete(final Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
