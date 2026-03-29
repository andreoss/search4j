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
 * <p>Files are never placed in the system temporary directory.</p>
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
     * Creates a new empty file with a unique name in a directory.
     *
     * @param directory Existing parent directory, not null
     * @param prefix Start of the file name
     * @param suffix End of the file name, null for {@code .tmp}
     * @return Path of the new file
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
     * Deletes a temporary file; an absent file is ignored.
     *
     * @param path File to delete
     * @throws IOException When the file cannot be removed
     */
    public static void delete(final Path path) throws IOException {
        Files.deleteIfExists(path);
    }
}
