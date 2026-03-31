/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of how {@link DatabaseFactory} resolves paths and flags.
 *
 * @since 0.1.0
 */
final class FactoryTest {

    @Test
    void opensVidrioDirectoryByDefault(@TempDir final Path dir) {
        final Path path = dir.resolve("db");
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                path, DatabaseAction.CREATE.code()
            )
        ) {
            db.addDocument(new DocumentInternal());
        }
        try (
            Database reader = DatabaseFactory.open(path);
            WritableDatabase writer = DatabaseFactory.openWritable(
                path, Backend.VIDRIO.add(DatabaseAction.OPEN.code()), 7
            )
        ) {
            Assertions.assertEquals(
                List.of(new TermCount(1), new TermCount(1)),
                List.of(reader.docCount(), writer.docCount()),
                "Directory detected by its version file"
            );
        }
    }

    @Test
    void rejectsCreateOverExisting(@TempDir final Path dir) {
        DatabaseFactory.openWritable(dir, DatabaseAction.CREATE.code()).close();
        Assertions.assertThrows(
            DatabaseCreateError.class,
            () -> DatabaseFactory.openWritable(dir, DatabaseAction.CREATE.code()),
            "Exists"
        );
    }

    @Test
    void rejectsPathsWithoutDatabase(@TempDir final Path dir) {
        Assertions.assertThrows(
            DatabaseNotFoundError.class, () -> DatabaseFactory.open(dir),
            "Directory without version file"
        );
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> DatabaseFactory.open(dir.resolve("absent")),
            "Nothing at all"
        );
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> DatabaseFactory.openWritable(
                dir.resolve("absent"), DatabaseAction.OPEN.code()
            ),
            "Open needs a database"
        );
    }

    @Test
    void opensInMemoryByFlag(@TempDir final Path dir) {
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                dir, Backend.IN_MEMORY.add(0)
            )
        ) {
            db.addDocument(new DocumentInternal());
            Assertions.assertEquals(
                List.of(new TermCount(1), false),
                List.of(db.docCount(), Files.exists(dir.resolve("postlist"))),
                "Nothing on disk"
            );
        }
    }

    @Test
    void opensStubFileListingOneDatabase(@TempDir final Path dir) throws IOException {
        final Path stub = dir.resolve("stub");
        Files.write(stub, List.of("vidrio db"), StandardCharsets.UTF_8);
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                stub, DatabaseAction.CREATE_OR_OPEN.code()
            )
        ) {
            db.addDocument(new DocumentInternal());
        }
        try (
            Database reader = DatabaseFactory.open(stub);
            WritableDatabase flagged = DatabaseFactory.openWritable(
                stub, Backend.STUB.add(DatabaseAction.OPEN.code())
            )
        ) {
            Assertions.assertEquals(
                List.of(new TermCount(1), new TermCount(1), true),
                List.of(
                    reader.docCount(), flagged.docCount(),
                    Files.isDirectory(dir.resolve("db"))
                ),
                "Stub resolved against its directory"
            );
        }
    }

    @Test
    void opensStubFileNamingMemory(@TempDir final Path dir) throws IOException {
        final Path stub = dir.resolve("stub");
        Files.write(stub, List.of("inmemory"), StandardCharsets.UTF_8);
        try (
            WritableDatabase writer = DatabaseFactory.openWritable(stub, 0);
            Database reader = DatabaseFactory.open(stub)
        ) {
            writer.addDocument(new DocumentInternal());
            Assertions.assertEquals(
                List.of(new TermCount(1), TermCount.ZERO),
                List.of(writer.docCount(), reader.docCount()),
                "Fresh in-memory databases"
            );
        }
    }

    @Test
    void emptyStubOpensNothingOrFails(@TempDir final Path dir) throws IOException {
        final Path stub = dir.resolve("stub");
        Files.write(stub, List.of("# none"), StandardCharsets.UTF_8);
        try (Database reader = DatabaseFactory.open(stub)) {
            Assertions.assertEquals(
                TermCount.ZERO, reader.docCount(), "Empty database"
            );
        }
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> DatabaseFactory.openWritable(stub, 0),
            "No databases listed"
        );
    }

    @Test
    void rejectsUnsupportedStubs(@TempDir final Path dir) throws IOException {
        final Path several = dir.resolve("several");
        Files.write(several, List.of("inmemory", "inmemory"), StandardCharsets.UTF_8);
        final Path remote = dir.resolve("remote");
        Files.write(remote, List.of("remote host:1234"), StandardCharsets.UTF_8);
        Assertions.assertAll(
            "Combining and remote databases",
            () -> Assertions.assertThrows(
                UnimplementedError.class, () -> DatabaseFactory.open(several),
                "Two databases"
            ),
            () -> Assertions.assertThrows(
                UnimplementedError.class, () -> DatabaseFactory.open(remote),
                "Remote database"
            ),
            () -> Assertions.assertThrows(
                UnimplementedError.class,
                () -> DatabaseFactory.openWritable(remote, 0),
                "Remote writable database"
            )
        );
    }
}
