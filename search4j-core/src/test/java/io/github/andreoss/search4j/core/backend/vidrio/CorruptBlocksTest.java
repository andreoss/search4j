/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DatabaseModifiedError;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that damaged reference blocks and mismatched root records are
 * rejected on read; the format has no block checksum.
 *
 * @since 0.1.0
 */
final class CorruptBlocksTest {

    /**
     * Offset of the directory end in a block header.
     */
    private static final int DIR_END = 9;

    /**
     * Offset of the first directory entry.
     */
    private static final int FIRST_ITEM = 11;

    @Test
    void rejectsBlockOfWrongLevel(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        data[4] = 1;
        final Path path = CorruptBlocksTest.write(dir, data);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(0, 1L)),
            "Leaf claims level 1"
        );
    }

    @Test
    void rejectsBlockOfLaterRevision(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        ByteBuffer.wrap(data).putInt(0, 2);
        final Path path = CorruptBlocksTest.write(dir, data);
        Assertions.assertThrows(
            DatabaseModifiedError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(0, 1L)),
            "Block newer than the revision read"
        );
    }

    @Test
    void rejectsDirectoryPastBlock(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        ByteBuffer.wrap(data).putShort(
            CorruptBlocksTest.DIR_END, (short) 0xFFF0
        );
        final Path path = CorruptBlocksTest.write(dir, data);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(0, 1L)),
            "Directory end past the block"
        );
    }

    @Test
    void rejectsDirectoryInsideHeader(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        ByteBuffer.wrap(data).putShort(CorruptBlocksTest.DIR_END, (short) 5);
        final Path path = CorruptBlocksTest.write(dir, data);
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(0, 1L)),
            "Directory end inside the header"
        );
    }

    @Test
    void rejectsItemPastBlock(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        ByteBuffer.wrap(data).putShort(
            CorruptBlocksTest.FIRST_ITEM + 2, (short) 0xFFFE
        );
        final VidrioTable table = VidrioTable.open(
            CorruptBlocksTest.write(dir, data), CorruptBlocksTest.root(0, 1L)
        );
        try (table) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            Assertions.assertThrows(
                DatabaseCorruptError.class, cursor::next,
                "Item offset past the block"
            );
        }
    }

    @Test
    void rejectsKeyPastBlock(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        final int item = Short.toUnsignedInt(
            ByteBuffer.wrap(data).getShort(CorruptBlocksTest.FIRST_ITEM + 2)
        );
        data[item + 2] = (byte) 0xFF;
        final VidrioTable table = VidrioTable.open(
            CorruptBlocksTest.write(dir, data), CorruptBlocksTest.root(0, 1L)
        );
        try (table) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            Assertions.assertThrows(
                DatabaseCorruptError.class, cursor::next,
                "Key length past the block"
            );
        }
    }

    @Test
    void rejectsTruncatedBlock(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final byte[] data = CorruptBlocksTest.fixture("docdata");
        final Path path = CorruptBlocksTest.write(
            dir, Arrays.copyOf(data, data.length - 1)
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(0, 1L)),
            "One byte short"
        );
    }

    @Test
    void rejectsRootRecordPastFile(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final Path path = CorruptBlocksTest.write(
            dir, CorruptBlocksTest.fixture("docdata")
        );
        final TableRoot root = new TableRoot(
            new BlockNumber(1L), 0, BlockSize.MIN, new RevisionNumber(1L),
            false
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> VidrioTable.open(path, root),
            "Root block number past the file"
        );
    }

    @Test
    void rejectsRootRecordOfOtherBlockSize(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final Path path = CorruptBlocksTest.write(
            dir, CorruptBlocksTest.fixture("docdata")
        );
        final TableRoot root = new TableRoot(
            new BlockNumber(0L), 0, BlockSize.DEFAULT, new RevisionNumber(1L),
            false
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> VidrioTable.open(path, root),
            "Block size larger than the file"
        );
    }

    @Test
    void rejectsRootRecordOfBranchLevel(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final Path path = CorruptBlocksTest.write(
            dir, CorruptBlocksTest.fixture("docdata")
        );
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, CorruptBlocksTest.root(2, 1L)),
            "Record says level 2, block is a leaf"
        );
    }

    @Test
    void rejectsVersionRecordRootAgainstTable(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final Path path = CorruptBlocksTest.write(
            dir, CorruptBlocksTest.fixture("docdata")
        );
        final VersionFile version = new VersionFile(dir);
        version.create(BlockSize.MIN);
        version.root(
            TableType.DOCDATA,
            new TableState(
                new TableRoot(
                    new BlockNumber(0L), 1, BlockSize.MIN,
                    new RevisionNumber(1L), false
                ),
                new EntryCount(113L), false, TableType.COMPRESS_MIN,
                FreeListInfo.EMPTY
            )
        );
        version.sync(
            version.write(new RevisionNumber(1L)), new RevisionNumber(1L)
        );
        final VersionFile read = new VersionFile(dir);
        read.read();
        final TableRoot root = read.root(TableType.DOCDATA).root();
        Assertions.assertThrows(
            DatabaseCorruptError.class, () -> VidrioTable.open(path, root),
            "Version record level does not match the block"
        );
    }

    @Test
    void readsIntactFixture(@TempDir final Path dir)
        throws IOException, URISyntaxException {
        final VidrioTable table = VidrioTable.open(
            CorruptBlocksTest.write(dir, CorruptBlocksTest.fixture("docdata")),
            CorruptBlocksTest.root(0, 1L)
        );
        try (table) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            Assertions.assertTrue(cursor.next(), "First entry read");
        }
    }

    private static TableRoot root(final int level, final long revision) {
        return new TableRoot(
            new BlockNumber(0L), level, BlockSize.MIN,
            new RevisionNumber(revision), false
        );
    }

    private static Path write(final Path dir, final byte[] data)
        throws IOException {
        return Files.write(dir.resolve("table.bin"), data);
    }

    private static byte[] fixture(final String name)
        throws IOException, URISyntaxException {
        return Files.readAllBytes(
            Path.of(
                CorruptBlocksTest.class.getResource(
                    String.format("oversized/%s.bin", name)
                ).toURI()
            )
        );
    }
}
