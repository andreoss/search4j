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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link TempFiles}.
 *
 * @since 0.1.0
 */
final class TempFilesTest {

    @Test
    void createsTempFileInDirectory(@TempDir final Path dir)
        throws IOException {
        Assertions.assertEquals(
            dir,
            TempFiles.create(dir, "prefix", ".tmp").getParent(),
            "File inside directory"
        );
    }

    @Test
    void deletesTempFile(@TempDir final Path dir) throws IOException {
        final Path file = TempFiles.create(dir, "prefix", ".tmp");
        TempFiles.delete(file);
        Assertions.assertFalse(Files.exists(file), "Deleted");
    }
}
