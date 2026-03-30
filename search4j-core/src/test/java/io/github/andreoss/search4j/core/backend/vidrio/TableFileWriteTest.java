/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link TableFile} written through {@link TableWriter}.
 *
 * @since 0.1.0
 */
final class TableFileWriteTest {

    @Test
    void writesTableReadBackFromFile(@TempDir final Path dir) {
        final Path path = dir.resolve("table.bin");
        final TableState first;
        try (
            TableWriter table =
                TableWriter.create(TableFile.create(path, BlockSize.MIN), 0)
        ) {
            for (int idx = 0; idx < 500; ++idx) {
                table.add(TableFileWriteTest.key(idx), Trees.key("first"));
            }
            first = table.commit(new RevisionNumber(1L));
        }
        final TableState second;
        try (
            TableWriter table =
                TableWriter.open(TableFile.update(path, BlockSize.MIN), first)
        ) {
            table.add(TableFileWriteTest.key(7), Trees.key("second"));
            table.delete(TableFileWriteTest.key(8));
            second = table.commit(new RevisionNumber(2L));
        }
        try (VidrioTable table = VidrioTable.open(path, second.root())) {
            Assertions.assertEquals(
                List.of(
                    "second", Optional.empty(), "first", 499L,
                    first.root().level() > 0
                ),
                List.of(
                    new String(
                        table.get(TableFileWriteTest.key(7)).orElseThrow(),
                        StandardCharsets.US_ASCII
                    ),
                    table.get(TableFileWriteTest.key(8)),
                    new String(
                        table.get(TableFileWriteTest.key(9)).orElseThrow(),
                        StandardCharsets.US_ASCII
                    ),
                    second.entries().value(), true
                ),
                "Both revisions written to the file"
            );
        }
    }

    @Test
    void replacesExistingFileOnCreate(@TempDir final Path dir) throws Exception {
        final Path path = dir.resolve("table.bin");
        Files.write(path, new byte[BlockSize.MIN.bytes() * 3]);
        TableFile.create(path, BlockSize.MIN).close();
        Assertions.assertEquals(0L, Files.size(path), "Truncated");
    }

    @Test
    void rejectsUpdateOfMissingFile(@TempDir final Path dir) {
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> TableFile.update(dir.resolve("missing.bin"), BlockSize.MIN),
            "Missing"
        );
    }

    @Test
    void rejectsCreateInMissingDirectory(@TempDir final Path dir) {
        Assertions.assertThrows(
            DatabaseCreateError.class,
            () -> TableFile.create(
                dir.resolve("none").resolve("table.bin"), BlockSize.MIN
            ),
            "Missing directory"
        );
    }

    @Test
    void rejectsWriteToFileOpenedToRead() throws URISyntaxException {
        try (
            TableFile file = new TableFile(
                Path.of(
                    TableFileWriteTest.class
                        .getResource("oversized/docdata.bin").toURI()
                ),
                BlockSize.MIN
            )
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class,
                () -> file.write(
                    new BlockNumber(0L), new byte[BlockSize.MIN.bytes()]
                ),
                "Read only"
            );
        }
    }

    @Test
    void rejectsWriteAfterClose(@TempDir final Path dir) {
        final TableFile file =
            TableFile.create(dir.resolve("table.bin"), BlockSize.MIN);
        file.close();
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> file.write(new BlockNumber(0L), new byte[BlockSize.MIN.bytes()]),
            "Closed"
        );
    }

    @Test
    void rejectsBlockOfWrongLength(@TempDir final Path dir) {
        try (
            TableFile file =
                TableFile.create(dir.resolve("table.bin"), BlockSize.MIN)
        ) {
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> file.write(new BlockNumber(0L), new byte[100]),
                "Short block"
            );
        }
    }

    @Test
    void writesBlockAtItsOffset(@TempDir final Path dir) throws Exception {
        final Path path = dir.resolve("table.bin");
        try (TableFile file = TableFile.create(path, BlockSize.MIN)) {
            file.write(
                new BlockNumber(2L), Block.fakeRoot(BlockSize.MIN).copy()
            );
        }
        try (TableFile file = new TableFile(path, BlockSize.MIN)) {
            Assertions.assertEquals(
                List.of(BlockSize.MIN.bytes() * 3L, 1),
                List.of(Files.size(path), file.read(new BlockNumber(2L)).entries()),
                "Third block written"
            );
        }
    }

    private static byte[] key(final int num) {
        return Trees.key(String.format("entry%05d", num));
    }
}
