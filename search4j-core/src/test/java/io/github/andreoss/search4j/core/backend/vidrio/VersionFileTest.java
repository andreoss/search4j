/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.DatabaseVersionError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link VersionFile}.
 *
 * @since 0.1.0
 */
final class VersionFileTest {

    /**
     * Length of the version record at the start of the reference
     * single-file databases.
     */
    private static final int RECORD = 89;

    /**
     * Offset of the root flags of the first table in the reference
     * single-file databases.
     */
    private static final int FLAGS = 34;

    /**
     * Offset of the format version in a version record.
     */
    private static final int VERSION = 14;

    /**
     * Identifier of the reference single-file databases.
     */
    private static final String UUID = "f4f4b3b5-a04e-45aa-9905-9b459d6bf2bf";

    @Test
    void readsReferenceRecord(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = VersionFileTest.reference(dir);
        final FreeListInfo list = new FreeListInfo(
            new RevisionNumber(0L), new BlockNumber(1L), FreeListMark.START,
            FreeListMark.START
        );
        Assertions.assertEquals(
            List.of(
                new RevisionNumber(1L), Uuid.parse(VersionFileTest.UUID),
                new TableState(
                    TableRoot.empty(BlockSize.MIN, new RevisionNumber(1L)),
                    EntryCount.EMPTY, true, 0, list
                ),
                VersionFileTest.initial(BlockSize.MIN, TableType.COMPRESS_MIN),
                new TableState(
                    TableRoot.empty(BlockSize.MIN, new RevisionNumber(1L)),
                    EntryCount.EMPTY, true, TableType.COMPRESS_MIN, list
                ),
                VersionFileTest.initial(BlockSize.MIN, 0),
                VersionFileTest.initial(BlockSize.MIN, TableType.COMPRESS_MIN),
                VersionFileTest.initial(BlockSize.MIN, TableType.COMPRESS_MIN),
                DatabaseStats.EMPTY
            ),
            List.of(
                version.revision(), version.uuid(),
                version.root(TableType.POSTLIST),
                version.root(TableType.DOCDATA),
                version.root(TableType.TERMLIST),
                version.root(TableType.POSITION),
                version.root(TableType.SPELLING),
                version.root(TableType.SYNONYM), version.stats()
            ),
            "Revision, identifier, six roots and statistics"
        );
    }

    @Test
    void writesReferenceRecordBackByteForByte(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        Assertions.assertArrayEquals(
            VersionFileTest.repaired(1),
            VersionFileTest.reference(dir).bytes(new RevisionNumber(1L)),
            "Record of the reference database"
        );
    }

    @Test
    void rejectsReferenceCorruptRecords(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = new VersionFile(dir);
        for (int num = 1; num <= 4; ++num) {
            Files.write(
                dir.resolve(VidrioFormat.versionFileName()),
                VersionFileTest.fixture(num)
            );
            Assertions.assertThrows(
                DatabaseCorruptError.class, version::read,
                String.format("Reference corrupt database %d", num)
            );
        }
    }

    @Test
    void createsWritesAndReadsBack(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.DEFAULT);
        final Uuid uuid = version.uuid();
        version.stats(version.stats().added(5L).checked(3L).withLast(1L));
        final Path temp = version.write(new RevisionNumber(1L));
        final boolean synced = version.sync(temp, new RevisionNumber(1L));
        final VersionFile read = new VersionFile(dir);
        read.read();
        Assertions.assertEquals(
            List.of(
                true, false, uuid, new RevisionNumber(1L),
                new DatabaseStats(1L, 1L, 5L, 5L, 5L, 3L, 0L, 0L),
                VersionFileTest.initial(BlockSize.DEFAULT, 0),
                VersionFileTest.initial(
                    BlockSize.DEFAULT, TableType.COMPRESS_MIN
                )
            ),
            List.of(
                synced, Files.exists(temp), read.uuid(), read.revision(),
                read.stats(), read.root(TableType.POSTLIST),
                read.root(TableType.DOCDATA)
            ),
            "Version file renamed into place and read back"
        );
    }

    @Test
    void generatesDistinctIdentifiers(@TempDir final Path dir) {
        final VersionFile first = new VersionFile(dir);
        first.create(BlockSize.MIN);
        final VersionFile second = new VersionFile(dir);
        second.create(BlockSize.MIN);
        Assertions.assertEquals(
            List.of(false, false, Uuid.NIL),
            List.of(
                first.uuid().nil(), first.uuid().equals(second.uuid()),
                new VersionFile(dir).uuid()
            ),
            "Fresh identifier per creation, none before"
        );
    }

    @Test
    void startsAtRevisionZero(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        Assertions.assertEquals(
            List.of(new RevisionNumber(0L), DatabaseStats.EMPTY),
            List.of(version.revision(), version.stats()),
            "New database"
        );
    }

    @Test
    void writesNewRevisionOverOld(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.sync(
            version.write(new RevisionNumber(1L)), new RevisionNumber(1L)
        );
        final TableState state = new TableState(
            new TableRoot(
                new BlockNumber(4L), 1, BlockSize.MIN, new RevisionNumber(2L),
                false
            ),
            new EntryCount(100L), false, 0, FreeListInfo.EMPTY
        );
        version.root(TableType.POSTLIST, state);
        version.sync(
            version.write(new RevisionNumber(2L)), new RevisionNumber(2L)
        );
        final VersionFile read = new VersionFile(dir);
        read.read();
        Assertions.assertEquals(
            List.of(new RevisionNumber(2L), state, new RevisionNumber(2L)),
            List.of(
                read.revision(), read.root(TableType.POSTLIST),
                version.revision()
            ),
            "Second revision replaced the first"
        );
    }

    @Test
    void cancelsToSyncedRoots(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.sync(
            version.write(new RevisionNumber(1L)), new RevisionNumber(1L)
        );
        final TableState initial = version.root(TableType.TERMLIST);
        version.root(
            TableType.TERMLIST,
            TableState.initial(BlockSize.MAX, TableType.COMPRESS_MIN)
        );
        version.stats(version.stats().added(3L));
        version.cancel();
        Assertions.assertEquals(
            List.of(initial, DatabaseStats.EMPTY),
            List.of(version.root(TableType.TERMLIST), version.stats()),
            "Changes dropped"
        );
    }

    @Test
    void cancelsToWrittenStats(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.stats(version.stats().added(3L).withLast(1L));
        version.root(
            TableType.SPELLING,
            TableState.initial(BlockSize.MAX, TableType.COMPRESS_MIN)
        );
        version.write(new RevisionNumber(1L));
        version.stats(version.stats().added(4L));
        version.cancel();
        Assertions.assertEquals(
            List.of(
                DatabaseStats.EMPTY.added(3L).withLast(1L),
                TableState.initial(BlockSize.MIN, TableType.COMPRESS_MIN)
            ),
            List.of(version.stats(), version.root(TableType.SPELLING)),
            "Statistics as written, roots as synced"
        );
    }

    @Test
    void cancelsToReadState(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = VersionFileTest.reference(dir);
        final TableState read = version.root(TableType.POSTLIST);
        version.root(TableType.POSTLIST, TableState.initial(BlockSize.MAX, 0));
        version.stats(DatabaseStats.EMPTY.added(1L));
        version.cancel();
        Assertions.assertEquals(
            List.of(read, DatabaseStats.EMPTY),
            List.of(version.root(TableType.POSTLIST), version.stats()),
            "Back to the file"
        );
    }

    @Test
    void writeLeavesTempFileUntilSync(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        final Path temp = version.write(new RevisionNumber(1L));
        Assertions.assertEquals(
            List.of(dir.resolve(VersionFile.TEMP_NAME), true, false),
            List.of(
                temp, Files.exists(temp),
                Files.exists(dir.resolve(VidrioFormat.versionFileName()))
            ),
            "Temp file written, version file not made yet"
        );
    }

    @Test
    void secondWriteReplacesUnsyncedOne(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.write(new RevisionNumber(1L));
        final boolean synced = version.sync(
            version.write(new RevisionNumber(2L)), new RevisionNumber(2L)
        );
        final VersionFile read = new VersionFile(dir);
        read.read();
        Assertions.assertEquals(
            List.of(true, new RevisionNumber(2L)),
            List.of(synced, read.revision()),
            "Second temp file synced"
        );
    }

    @Test
    void syncFailsWithoutTempFile(@TempDir final Path dir) throws IOException {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        final Path temp = version.write(new RevisionNumber(1L));
        Files.delete(temp);
        Assertions.assertEquals(
            List.of(false, new RevisionNumber(0L), false),
            List.of(
                version.sync(temp, new RevisionNumber(1L)),
                version.revision(),
                Files.exists(dir.resolve(VidrioFormat.versionFileName()))
            ),
            "Rename failed, revision kept"
        );
    }

    @Test
    void syncFailsWhenDirectoryGone(@TempDir final Path dir)
        throws IOException {
        final Path sub = Files.createDirectory(dir.resolve("db"));
        final VersionFile version = new VersionFile(sub);
        version.create(BlockSize.MIN);
        final Path temp = version.write(new RevisionNumber(1L));
        Files.delete(temp);
        Files.delete(sub);
        Assertions.assertFalse(
            version.sync(temp, new RevisionNumber(1L)),
            "Directory removed"
        );
    }

    @Test
    void syncRemovesTempFileOnFailure(@TempDir final Path dir)
        throws IOException {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        final Path temp = version.write(new RevisionNumber(1L));
        Files.createDirectory(dir.resolve(VidrioFormat.versionFileName()));
        Assertions.assertEquals(
            List.of(false, false),
            List.of(
                version.sync(temp, new RevisionNumber(1L)), Files.exists(temp)
            ),
            "Rename over a directory failed, temp file removed"
        );
    }

    @Test
    void rejectsSyncOfOldRevision(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.sync(
            version.write(new RevisionNumber(2L)), new RevisionNumber(2L)
        );
        final Path temp = version.write(new RevisionNumber(2L));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> version.sync(temp, new RevisionNumber(2L)),
            "Revision not above the synced one"
        );
    }

    @Test
    void allowsAnyRevisionAfterZero(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        Assertions.assertTrue(
            version.sync(
                version.write(new RevisionNumber(0L)), new RevisionNumber(0L)
            ),
            "Revision 0 over 0"
        );
    }

    @Test
    void rejectsWriteBeforeCreate(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir);
        final RevisionNumber one = new RevisionNumber(1L);
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                InvalidOperationError.class, () -> version.write(one),
                "No roots to write"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class,
                () -> version.root(TableType.POSTLIST),
                "No root to read"
            )
        );
    }

    @Test
    void rejectsWriteIntoMissingDirectory(@TempDir final Path dir) {
        final VersionFile version = new VersionFile(dir.resolve("absent"));
        version.create(BlockSize.MIN);
        Assertions.assertThrows(
            DatabaseCreateError.class,
            () -> version.write(new RevisionNumber(1L)),
            "No such directory"
        );
    }

    @Test
    void rejectsMissingVersionFile(@TempDir final Path dir) {
        Assertions.assertThrows(
            DatabaseNotFoundError.class, new VersionFile(dir)::read,
            "Empty directory"
        );
    }

    @Test
    void rejectsDirectoryAsVersionFile(@TempDir final Path dir)
        throws IOException {
        Files.createDirectory(dir.resolve(VidrioFormat.versionFileName()));
        Assertions.assertThrows(
            DatabaseCorruptError.class, new VersionFile(dir)::read,
            "Not a file"
        );
    }

    @Test
    void rejectsShortFile(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = new VersionFile(dir);
        Assertions.assertAll(
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> version.parse(new byte[0]), "Empty file"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> version.parse(
                    Arrays.copyOf(VersionFileTest.repaired(1), 32)
                ),
                "Up to the identifier"
            ),
            () -> Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> version.parse(
                    Arrays.copyOf(VersionFileTest.repaired(1), 40)
                ),
                "Inside the root records"
            )
        );
    }

    @Test
    void rejectsBadMagic(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = VersionFileTest.repaired(1);
        data[VersionFileTest.VERSION - 1] ^= 1;
        final VersionFile version = new VersionFile(dir);
        Assertions.assertEquals(
            "Version file magic incorrect",
            Assertions.assertThrows(
                DatabaseCorruptError.class, () -> version.parse(data),
                "Last magic byte changed"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsOtherFormatVersion(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = VersionFileTest.repaired(1);
        data[VersionFileTest.VERSION] = 0x03;
        data[VersionFileTest.VERSION + 1] = (byte) 0x98;
        final VersionFile version = new VersionFile(dir);
        final DatabaseVersionError error = Assertions.assertThrows(
            DatabaseVersionError.class, () -> version.parse(data),
            "Version of 2015-12-24"
        );
        Assertions.assertTrue(
            error.getMessage().endsWith(
                "Database is format version 20151224 but only 20160314 is known"
            ),
            error.getMessage()
        );
    }

    @Test
    void rejectsJunkAfterStats(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = Arrays.copyOf(
            VersionFileTest.repaired(1), VersionFileTest.RECORD + 1
        );
        final VersionFile version = new VersionFile(dir);
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> version.parse(data),
            "One byte more than the record"
        );
    }

    @Test
    void readsRecordWithoutStats(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = new VersionFile(dir);
        version.parse(
            Arrays.copyOf(
                VersionFileTest.repaired(1), VersionFileTest.RECORD - 8
            )
        );
        Assertions.assertEquals(
            DatabaseStats.EMPTY, version.stats(), "No statistics stored"
        );
    }

    @Test
    void rejectsRootRecordOfBadLevel(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = VersionFileTest.repaired(1);
        data[VersionFileTest.FLAGS] = 0x07;
        final VersionFile version = new VersionFile(dir);
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> version.parse(data),
            "Fake root at level 1"
        );
    }

    @Test
    void keepsReadStateOnCorruptFile(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VersionFile version = VersionFileTest.reference(dir);
        Files.write(
            dir.resolve(VidrioFormat.versionFileName()),
            VersionFileTest.fixture(3)
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, version::read, "Corrupt file"
        );
        Assertions.assertEquals(
            new RevisionNumber(1L), version.revision(),
            "Revision from the first read"
        );
    }

    @Test
    void writesUuidAsStored(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        Assertions.assertEquals(
            VersionFileTest.UUID.replace("-", ""),
            HexFormat.of().formatHex(
                Arrays.copyOfRange(
                    VersionFileTest.reference(dir)
                        .bytes(new RevisionNumber(1L)),
                    VersionFileTest.VERSION + 2,
                    VersionFileTest.VERSION + 2 + Uuid.BYTES
                )
            ),
            "Identifier bytes after the version"
        );
    }

    private static TableState initial(
        final BlockSize size, final int compress
    ) {
        return new TableState(
            TableRoot.empty(size, new RevisionNumber(1L)), EntryCount.EMPTY,
            true, compress, FreeListInfo.EMPTY
        );
    }

    private static VersionFile reference(final Path dir)
        throws IOException, URISyntaxException {
        Files.write(
            dir.resolve(VidrioFormat.versionFileName()),
            VersionFileTest.repaired(1)
        );
        final VersionFile version = new VersionFile(dir);
        version.read();
        return version;
    }

    private static byte[] repaired(final int num)
        throws IOException, URISyntaxException {
        final byte[] data = Arrays.copyOf(
            VersionFileTest.fixture(num), VersionFileTest.RECORD
        );
        data[VersionFileTest.FLAGS] = 0x03;
        return data;
    }

    private static byte[] fixture(final int num)
        throws IOException, URISyntaxException {
        return Files.readAllBytes(
            Path.of(
                VersionFileTest.class.getResource(
                    String.format("single/db%d.bin", num)
                ).toURI()
            )
        );
    }
}
