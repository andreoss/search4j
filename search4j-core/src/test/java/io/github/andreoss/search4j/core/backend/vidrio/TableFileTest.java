/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TableFile}.
 *
 * @since 0.1.0
 */
final class TableFileTest {

    @Test
    void readsBlockOfRealFile() throws URISyntaxException {
        try (TableFile file = TableFileTest.file("oversized/docdata")) {
            final Block block = file.read(new BlockNumber(0L));
            Assertions.assertEquals(
                List.of(BlockSize.MIN, 113, new RevisionNumber(1L)),
                List.of(file.size(), block.entries(), block.revision()),
                "Document data block"
            );
        }
    }

    @Test
    void rejectsBlockPastEnd() throws URISyntaxException {
        try (TableFile file = TableFileTest.file("oversized/docdata")) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> file.read(new BlockNumber(1L)),
                "Only one block"
            );
        }
    }

    @Test
    void rejectsPartialBlock() throws URISyntaxException {
        final TableFile file = new TableFile(
            TableFileTest.path("oversized/docdata"), BlockSize.DEFAULT
        );
        try (file) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> file.read(new BlockNumber(0L)),
                "File shorter than a block"
            );
        }
    }

    @Test
    void rejectsReadAfterClose() throws URISyntaxException {
        final TableFile file = TableFileTest.file("oversized/docdata");
        file.close();
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> file.read(new BlockNumber(0L)),
            "Closed"
        );
    }

    @Test
    void rejectsMissingFile() throws URISyntaxException {
        final Path path = TableFileTest.path("oversized/docdata")
            .resolveSibling("missing.bin");
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> new TableFile(path, BlockSize.MIN),
            "Missing"
        );
    }

    @Test
    void rejectsDirectoryAsFile() throws URISyntaxException {
        final Path path = TableFileTest.path("oversized/docdata").getParent();
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> {
                try (TableFile file = new TableFile(path, BlockSize.MIN)) {
                    file.read(new BlockNumber(0L));
                }
            },
            "Directory"
        );
    }

    private static TableFile file(final String name) throws URISyntaxException {
        return new TableFile(TableFileTest.path(name), BlockSize.MIN);
    }

    private static Path path(final String name) throws URISyntaxException {
        return Path.of(
            TableFileTest.class.getResource(
                String.format("%s.bin", name)
            ).toURI()
        );
    }
}
