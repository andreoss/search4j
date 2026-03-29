/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the common utilities.
 */
package io.github.andreoss.search4j.core.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of file round trips through {@link FileOps}.
 *
 * @since 0.1.0
 */
final class FileOpsTest {

    @Test
    void writesAndReadsText(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("sample.txt");
        FileOps.writeAll(file, "hello");
        Assertions.assertEquals("hello", FileOps.readAll(file), "Round trip");
    }

    @Test
    void detectsRegularFile(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("sample.txt");
        Files.writeString(file, "x");
        Assertions.assertTrue(FileOps.isFile(file), "Regular file");
    }

    @Test
    void detectsDirectory(@TempDir final Path dir) throws IOException {
        Assertions.assertTrue(
            FileOps.isDirectory(Files.createDirectory(dir.resolve("sub"))),
            "Directory"
        );
    }

    @Test
    void deletesExistingFile(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("gone.txt");
        Files.writeString(file, "x");
        FileOps.delete(file);
        Assertions.assertFalse(Files.exists(file), "Deleted");
    }

    @Test
    void listsNamesInOrder(@TempDir final Path dir) throws IOException {
        Files.writeString(dir.resolve("b.txt"), "b");
        Files.writeString(dir.resolve("a.txt"), "a");
        Assertions.assertEquals(
            List.of(dir.resolve("a.txt"), dir.resolve("b.txt")),
            FileOps.list(dir),
            "Sorted entries"
        );
    }
}
