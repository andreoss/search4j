/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Byte identity of a database written through the public API against the
 * reference-produced database held as fixtures: 112 documents whose data
 * is {@code hello world} and whose term lists are empty, tables of 2048
 * byte blocks, one commit.
 *
 * <p>The reference database was damaged in one document data item after
 * it was written, so that table is compared outside the damaged item.</p>
 *
 * @since 0.1.0
 */
final class ReferenceLayoutTest {

    /**
     * Documents of the reference database.
     */
    private static final int DOCS = 112;

    /**
     * Data of every document.
     */
    private static final String DATA = "hello world";

    /**
     * Offset of the identifier in a version record.
     */
    private static final int UUID_START = 16;

    @Test
    void writesVersionFileAsReference(@TempDir final Path dir) throws IOException {
        ReferenceLayoutTest.build(dir);
        final byte[] expected = ReferenceLayoutTest.fixture("version");
        final byte[] actual = Files.readAllBytes(
            dir.resolve(VidrioFormat.versionFileName())
        );
        final int flags = ReferenceLayoutTest.UUID_START + Uuid.BYTES + 2;
        final byte sequential = actual[flags];
        for (final byte[] record : List.of(expected, actual)) {
            Arrays.fill(
                record, ReferenceLayoutTest.UUID_START,
                ReferenceLayoutTest.UUID_START + Uuid.BYTES, (byte) 0
            );
            record[flags] = 0;
        }
        Assertions.assertAll(
            "Version record apart from the identifier",
            () -> Assertions.assertArrayEquals(expected, actual, "Record"),
            () -> Assertions.assertEquals(
                0, sequential,
                "Posting list not sequential after the placeholder chunk was replaced"
            )
        );
    }

    @Test
    void writesTermListTableAsReference(@TempDir final Path dir)
        throws IOException {
        ReferenceLayoutTest.build(dir);
        Assertions.assertArrayEquals(
            ReferenceLayoutTest.fixture("termlist"),
            Files.readAllBytes(dir.resolve(TableType.TERMLIST.fileName())),
            "Term list table byte for byte"
        );
    }

    @Test
    void writesPostingListItemsAsReference(@TempDir final Path dir)
        throws IOException {
        ReferenceLayoutTest.build(dir);
        final Path fixture = dir.resolve("fixture.bin");
        Files.write(fixture, ReferenceLayoutTest.fixture("postlist"));
        final List<String> expected = ReferenceLayoutTest.items(fixture);
        final List<String> actual = ReferenceLayoutTest.items(
            dir.resolve(TableType.POSTLIST.fileName())
        );
        final byte[] block = Files.readAllBytes(
            dir.resolve(TableType.POSTLIST.fileName())
        );
        Assertions.assertAll(
            "Posting list entries byte for byte, packed after a replaced item",
            () -> Assertions.assertEquals(expected, actual, "Entries"),
            () -> Assertions.assertEquals(1, expected.size(), "One entry"),
            () -> Assertions.assertEquals(
                List.of(0x06, 0xFB, 0x07, 0x05),
                List.of(
                    block[5] & 0xFF, block[6] & 0xFF, block[7] & 0xFF,
                    block[8] & 0xFF
                ),
                "Largest free run ten bytes below the total"
            )
        );
    }

    @Test
    void writesDocumentDataTableAsReferenceOutsideDamage(@TempDir final Path dir)
        throws IOException {
        ReferenceLayoutTest.build(dir);
        final byte[] expected = ReferenceLayoutTest.fixture("docdata");
        final byte[] actual = Files.readAllBytes(
            dir.resolve(TableType.DOCDATA.fileName())
        );
        final List<Integer> differing = new ArrayList<>(0);
        for (int idx = 0; idx < expected.length; ++idx) {
            if (expected[idx] != actual[idx]) {
                differing.add(idx);
            }
        }
        Assertions.assertEquals(
            List.of(expected.length, true),
            List.of(actual.length, differing.size() <= 2),
            String.format("Only the damaged item differs: %s", differing)
        );
    }

    @Test
    void createsOnlyTheTablesWritten(@TempDir final Path dir) {
        ReferenceLayoutTest.build(dir);
        Assertions.assertEquals(
            List.of(true, true, true, false, false, false, true),
            List.of(
                Files.exists(dir.resolve(TableType.POSTLIST.fileName())),
                Files.exists(dir.resolve(TableType.DOCDATA.fileName())),
                Files.exists(dir.resolve(TableType.TERMLIST.fileName())),
                Files.exists(dir.resolve(TableType.POSITION.fileName())),
                Files.exists(dir.resolve(TableType.SPELLING.fileName())),
                Files.exists(dir.resolve(TableType.SYNONYM.fileName())),
                Files.exists(dir.resolve(VidrioFormat.lockFileName()))
            ),
            "Lazy tables absent, lock file left as the reference leaves it"
        );
    }

    @Test
    void readsReferenceDatabaseDirectory(@TempDir final Path dir)
        throws IOException {
        Files.write(
            dir.resolve(VidrioFormat.versionFileName()),
            ReferenceLayoutTest.fixture("version")
        );
        for (final TableType type : List.of(
            TableType.POSTLIST, TableType.DOCDATA, TableType.TERMLIST
        )) {
            Files.write(
                dir.resolve(type.fileName()),
                ReferenceLayoutTest.fixture(type.tableName())
            );
        }
        try (Database db = DatabaseFactory.open(dir)) {
            Assertions.assertEquals(
                List.of(
                    new TermCount(ReferenceLayoutTest.DOCS),
                    DocId.of(ReferenceLayoutTest.DOCS), ReferenceLayoutTest.DATA,
                    TermCount.ZERO, false, false
                ),
                List.of(
                    db.docCount(), db.lastDocId(),
                    db.document(DocId.of(1)).data(), db.docLength(DocId.of(50)),
                    db.hasPositions(), db.allTerms("").hasNext()
                ),
                "Reference database opened through the public API"
            );
        }
    }

    private static List<String> items(final Path path) {
        final List<String> result = new ArrayList<>(0);
        try (
            VidrioTable table = VidrioTable.open(
                path,
                new TableRoot(
                    new BlockNumber(0L), 0, BlockSize.MIN, new RevisionNumber(1L),
                    false
                )
            )
        ) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            while (cursor.next()) {
                result.add(
                    String.format(
                        "%s=%s", HexFormat.of().formatHex(cursor.key().data()),
                        HexFormat.of().formatHex(cursor.tag())
                    )
                );
            }
        }
        return result;
    }

    private static void build(final Path dir) {
        try (
            WritableDatabase db = DatabaseFactory.openWritable(
                dir, DatabaseAction.CREATE.code(), BlockSize.MIN_BYTES
            )
        ) {
            for (int idx = 0; idx < ReferenceLayoutTest.DOCS; ++idx) {
                final DocumentInternal doc = new DocumentInternal();
                doc.setData(ReferenceLayoutTest.DATA);
                db.addDocument(doc);
            }
            db.commit();
        }
    }

    private static byte[] fixture(final String name) throws IOException {
        try (
            InputStream stream = ReferenceLayoutTest.class.getResourceAsStream(
                String.format("oversized/%s.bin", name)
            )
        ) {
            return stream.readAllBytes();
        }
    }
}
