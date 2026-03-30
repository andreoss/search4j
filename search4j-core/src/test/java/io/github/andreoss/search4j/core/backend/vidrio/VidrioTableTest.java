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
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Reads table files written by the reference implementation.
 *
 * @since 0.1.0
 */
final class VidrioTableTest {

    /**
     * Data of every document in the fixtures.
     */
    private static final String HELLO = "hello world";

    @Test
    void iteratesDocumentDataInOrder() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/docdata")) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            final List<DocId> docs = new ArrayList<>(112);
            while (cursor.next()) {
                docs.add(DocDataKeys.parse(cursor.key()));
            }
            Assertions.assertEquals(
                List.of(112, DocId.of(1), DocId.of(112), true),
                List.of(
                    docs.size(), docs.get(0), docs.get(111),
                    cursor.afterEnd()
                ),
                "Documents 1 to 112 in order"
            );
        }
    }

    @Test
    void readsEveryDocumentData() throws URISyntaxException {
        final List<String> data = new ArrayList<>(111);
        try (VidrioTable table = VidrioTableTest.table("oversized/docdata")) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            while (data.size() < 111 && cursor.next()) {
                data.add(new String(cursor.tag(), StandardCharsets.US_ASCII));
            }
        }
        Assertions.assertEquals(
            Collections.nCopies(111, VidrioTableTest.HELLO), data,
            "Data of documents 1 to 111"
        );
    }

    @Test
    void detectsOversizedItemOnRead() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/docdata")) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> table.get(VidrioTableTest.doc(112)),
                "Item past the block end"
            );
        }
    }

    @Test
    void readsOverlappingItemAsStored() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/docdata2")) {
            Assertions.assertEquals(
                1041, table.get(VidrioTableTest.doc(112)).orElseThrow().length,
                "Oversized item that fits the block is read like the reference"
            );
        }
    }

    @Test
    void looksUpDocumentData() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/docdata")) {
            Assertions.assertEquals(
                List.of(VidrioTableTest.HELLO, false, true, false),
                List.of(
                    new String(
                        table.get(VidrioTableTest.doc(57)).orElseThrow(),
                        StandardCharsets.US_ASCII
                    ),
                    table.get(VidrioTableTest.doc(113)).isPresent(),
                    table.contains(VidrioTableTest.doc(1)),
                    table.contains(VidrioTableTest.doc(200))
                ),
                "Present and absent documents"
            );
        }
    }

    @Test
    void iteratesEmptyTermLists() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/termlist")) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            int count = 0;
            int bytes = 0;
            while (cursor.next()) {
                bytes += cursor.tag().length;
                ++count;
            }
            Assertions.assertEquals(
                List.of(112, 0), List.of(count, bytes), "Empty term lists"
            );
        }
    }

    @Test
    void readsPostingChunk() throws URISyntaxException {
        final List<Object> seen = new ArrayList<>(4);
        try (VidrioTable table = VidrioTableTest.table("oversized/postlist")) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            seen.add(cursor.next());
            seen.add(cursor.key().toString());
            seen.add(cursor.tag().length);
            seen.add(cursor.next());
        }
        Assertions.assertEquals(
            List.of(true, "00e0", 228, false), seen, "One posting entry"
        );
    }

    @Test
    void findsNoOverlongTerm() throws URISyntaxException {
        try (VidrioTable table = VidrioTableTest.table("oversized/termlist")) {
            final StringBuilder term = new StringBuilder(300);
            while (term.length() < 300) {
                term.append('x');
                final byte[] key = Trees.key(term.toString());
                Assertions.assertEquals(
                    List.of(false, false),
                    List.of(table.get(key).isPresent(), table.contains(key)),
                    String.format("Term of %d bytes", term.length())
                );
            }
        }
    }

    @Test
    void rejectsNonTableFiles() throws URISyntaxException {
        for (int num = 1; num <= 4; ++num) {
            final Path path = VidrioTableTest.path(
                String.format("single/db%d", num)
            );
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> VidrioTable.open(path, VidrioTableTest.root(0, 7L)),
                String.format("Database %d", num)
            );
        }
    }

    @Test
    void rejectsRootOfWrongLevel() throws URISyntaxException {
        final Path path = VidrioTableTest.path("oversized/docdata");
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            () -> VidrioTable.open(path, VidrioTableTest.root(1, 7L)),
            "Leaf given as level 1"
        );
    }

    @Test
    void rejectsRootNewerThanRevision() throws URISyntaxException {
        final Path path = VidrioTableTest.path("oversized/docdata");
        Assertions.assertThrows(
            DatabaseModifiedError.class,
            () -> VidrioTable.open(path, VidrioTableTest.root(0, 0L)),
            "Root written by revision 1"
        );
    }

    @Test
    void rejectsMissingFile() throws URISyntaxException {
        final Path path = VidrioTableTest.path("oversized/docdata")
            .resolveSibling("absent.bin");
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> VidrioTable.open(
                path, TableRoot.empty(BlockSize.MIN, Trees.REVISION)
            ),
            "No such file"
        );
    }

    @Test
    void readsFakeRootWithoutDisk() throws URISyntaxException {
        final VidrioTable table = VidrioTable.open(
            VidrioTableTest.path("oversized/postlist"),
            TableRoot.empty(BlockSize.MIN, Trees.REVISION)
        );
        try (table) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            Assertions.assertEquals(
                List.of(ItemKey.EMPTY, 0, false, false),
                List.of(
                    cursor.key(), cursor.tag().length, cursor.next(),
                    table.contains(Trees.key("a"))
                ),
                "Only the root item"
            );
        }
    }

    private static TableRoot root(final int level, final long revision) {
        return new TableRoot(
            new BlockNumber(0L), level, BlockSize.MIN,
            new RevisionNumber(revision), false
        );
    }

    private static byte[] doc(final int num) {
        return DocDataKeys.of(DocId.of(num)).data();
    }

    private static VidrioTable table(final String name)
        throws URISyntaxException {
        return VidrioTable.open(
            VidrioTableTest.path(name), VidrioTableTest.root(0, 1L)
        );
    }

    private static Path path(final String name) throws URISyntaxException {
        return Path.of(
            VidrioTableTest.class.getResource(
                String.format("%s.bin", name)
            ).toURI()
        );
    }
}
