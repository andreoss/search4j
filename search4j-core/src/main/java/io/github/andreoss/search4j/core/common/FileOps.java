/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Common utilities of the search engine.
 */
package io.github.andreoss.search4j.core.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File and directory operations used by the storage layer.
 *
 * <p>Every path is handled through {@link Path}. Temporary files are created
 * under a caller-provided directory, never the system temporary directory.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class FileOps {

    /**
     * Utility class, no instances.
     */
    private FileOps() {
    }

    /**
     * Reads the whole contents of a file as a UTF-8 string.
     *
     * @param path File to read
     * @return Contents decoded as UTF-8
     * @throws IOException When the file cannot be read
     */
    public static String readAll(final Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Writes text to a file, creating or truncating it.
     *
     * @param path File to write
     * @param content Text written as UTF-8
     * @throws IOException When the file cannot be written
     */
    public static void writeAll(final Path path, final String content)
        throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Tests whether a path names an existing regular file.
     *
     * @param path Path to test
     * @return True for a regular file, false when absent
     */
    public static boolean isFile(final Path path) {
        return Files.isRegularFile(path);
    }

    /**
     * Tests whether a path names an existing directory.
     *
     * @param path Path to test
     * @return True for a directory, false when absent
     */
    public static boolean isDirectory(final Path path) {
        return Files.isDirectory(path);
    }

    /**
     * Deletes a file or an empty directory; an absent path is ignored.
     *
     * @param path Path to delete
     * @throws IOException When the path cannot be removed
     */
    public static void delete(final Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    /**
     * Lists the entries of a directory in name order.
     *
     * @param path Directory to list, not null
     * @return Entries sorted by path
     * @throws IOException When the directory cannot be listed
     */
    public static List<Path> list(final Path path) throws IOException {
        Objects.requireNonNull(path, "directory");
        final List<Path> entries;
        try (Stream<Path> stream = Files.list(path)) {
            entries = stream.sorted().collect(Collectors.toList());
        }
        return entries;
    }
}
