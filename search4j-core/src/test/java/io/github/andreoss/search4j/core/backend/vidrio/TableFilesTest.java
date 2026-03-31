/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link TableFiles}, {@link ReadTables} and {@link WriteTables}.
 *
 * @since 0.1.0
 */
final class TableFilesTest {

    @Test
    void readsAbsentOptionalTableAsEmpty(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        try (
            VidrioTable table = files.read(
                TableType.POSITION, TableState.initial(BlockSize.MIN, 0)
            )
        ) {
            Assertions.assertEquals(
                List.of(false, false, dir.resolve(TableType.POSITION.fileName())),
                List.of(
                    table.contains(new byte[] {1}),
                    files.exists(TableType.POSITION), files.path(TableType.POSITION)
                ),
                "Empty table without a file"
            );
        }
    }

    @Test
    void rejectsAbsentEagerTable(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final TableState initial = TableState.initial(BlockSize.MIN, 0);
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> files.read(TableType.POSTLIST, initial),
            "Posting list table is not optional"
        );
    }

    @Test
    void rejectsAbsentOptionalTableWithRootOnDisk(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final TableState used = new TableState(
            new TableRoot(
                new BlockNumber(0L), 0, BlockSize.MIN, new RevisionNumber(1L), false
            ),
            new EntryCount(1L), true, 0, FreeListInfo.EMPTY
        );
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> files.read(TableType.POSITION, used),
            "A root on disk needs the file"
        );
    }

    @Test
    void createsUpdatesAndRemoves(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final TableState initial = TableState.initial(BlockSize.MIN, 0);
        final TableState first;
        try (TableWriter writer = files.create(TableType.POSITION, initial)) {
            writer.add(new byte[] {1}, new byte[] {2});
            first = writer.commit(new RevisionNumber(1L));
        }
        final boolean created = files.exists(TableType.POSITION);
        final boolean kept;
        try (TableWriter writer = files.update(TableType.POSITION, first)) {
            kept = writer.contains(new byte[] {1});
        }
        files.remove(TableType.POSITION);
        files.remove(TableType.POSITION);
        Assertions.assertEquals(
            List.of(true, true, false),
            List.of(created, kept, files.exists(TableType.POSITION)),
            "File created, entry kept across reopening, file removed"
        );
    }

    @Test
    void reportsUnremovableFile(@TempDir final Path dir) throws IOException {
        final TableFiles files = new TableFiles(dir);
        Files.createDirectories(
            files.path(TableType.POSITION).resolve("inside")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> files.remove(TableType.POSITION),
            "A directory in the way"
        );
    }

    @Test
    void writeTablesCreateEagerAndOpenLazyOnFirstWrite(@TempDir final Path dir)
        throws IOException {
        final TableFiles files = new TableFiles(dir);
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        Files.write(files.path(TableType.POSITION), new byte[] {1});
        final WriteTables tables = WriteTables.create(files, version);
        final List<Boolean> created = List.of(
            files.exists(TableType.POSTLIST), files.exists(TableType.TERMLIST),
            files.exists(TableType.DOCDATA), files.exists(TableType.POSITION),
            tables.opened(TableType.POSITION).isPresent(),
            tables.lookup(TableType.POSITION).contains(new byte[] {1}),
            tables.modified()
        );
        tables.writer(TableType.POSITION).add(new byte[] {1}, new byte[] {2});
        tables.commit(new RevisionNumber(1L));
        final List<Object> committed = List.of(
            files.exists(TableType.POSITION),
            tables.entries(TableType.POSITION), tables.entries(TableType.SPELLING),
            version.root(TableType.POSITION).root().fake(),
            version.root(TableType.POSTLIST).root().fake(),
            version.root(TableType.SPELLING).root().fake(), tables.modified()
        );
        tables.close();
        Assertions.assertEquals(
            List.of(
                List.of(true, true, false, false, false, false, false),
                List.of(
                    true, new EntryCount(1L), EntryCount.EMPTY, false, false,
                    true, false
                )
            ),
            List.of(created, committed),
            "Eager tables created, lazy ones on first write, untouched ones fake"
        );
    }

    @Test
    void writeTablesCancelToVersionRoots(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        try (WriteTables tables = WriteTables.create(files, version)) {
            tables.writer(TableType.POSTLIST).add(new byte[] {1}, new byte[] {2});
            tables.commit(new RevisionNumber(1L));
            tables.writer(TableType.POSTLIST).add(new byte[] {3}, new byte[] {4});
            tables.writer(TableType.DOCDATA).add(new byte[] {3}, new byte[] {4});
            tables.cancel();
            Assertions.assertEquals(
                List.of(true, false, false, false),
                List.of(
                    tables.lookup(TableType.POSTLIST).contains(new byte[] {1}),
                    tables.lookup(TableType.POSTLIST).contains(new byte[] {3}),
                    tables.lookup(TableType.DOCDATA).contains(new byte[] {3}),
                    tables.modified()
                ),
                "Uncommitted entries dropped"
            );
        }
    }

    @Test
    void writeTablesOpenRejectMissingEagerFile(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        Assertions.assertThrows(
            DatabaseNotFoundError.class, () -> WriteTables.open(files, version),
            "No posting list file"
        );
        Assertions.assertThrows(
            DatabaseNotFoundError.class, () -> ReadTables.open(files, version),
            "No posting list file to read"
        );
    }

    @Test
    void readTablesOpenEveryTable(@TempDir final Path dir) {
        final TableFiles files = new TableFiles(dir);
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        try (WriteTables tables = WriteTables.create(files, version)) {
            tables.writer(TableType.POSTLIST).add(new byte[] {1}, new byte[] {2});
            tables.commit(new RevisionNumber(1L));
        }
        try (ReadTables tables = ReadTables.open(files, version)) {
            Assertions.assertEquals(
                List.of(true, false, new EntryCount(1L), EntryCount.EMPTY),
                List.of(
                    tables.lookup(TableType.POSTLIST).contains(new byte[] {1}),
                    tables.lookup(TableType.POSITION).contains(new byte[] {1}),
                    tables.entries(TableType.POSTLIST),
                    tables.entries(TableType.POSITION)
                ),
                "Committed entry read, absent table empty"
            );
        }
    }
}
