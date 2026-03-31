/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermListTable} and {@link TermListWriter}: the reference
 * fixture read and reproduced, lookups and changes.
 *
 * @since 0.1.0
 */
final class TermListTableTest {

    /**
     * Compression threshold of the term list table.
     */
    private static final int COMPRESS = 18;

    /**
     * Number of documents of the reference fixture.
     */
    private static final int DOCS = 112;

    @Test
    void readsReferenceFixture() throws URISyntaxException {
        final List<Object> seen = new ArrayList<>(6);
        final VidrioTable postings = TermListTableTest.fixture("postlist");
        try (VidrioTable terms = TermListTableTest.fixture("termlist"); postings) {
            final TermListTable table =
                TermListTable.of(terms, PostingTable.of(postings));
            int empty = 0;
            for (int doc = 1; doc <= TermListTableTest.DOCS; ++doc) {
                final VidrioTermList list = table.open(DocId.of(doc));
                if (Documents.read(list).isEmpty()
                    && list.docLength().equals(TermCount.ZERO)
                    && list.approxSize().equals(TermCount.ZERO)
                    && list.uniqueTerms().equals(TermCount.ZERO)) {
                    ++empty;
                }
            }
            seen.add(empty);
            seen.add(table.contains(DocId.of(TermListTableTest.DOCS)));
            seen.add(table.contains(DocId.of(TermListTableTest.DOCS + 1)));
            seen.add(table.find(DocId.of(TermListTableTest.DOCS + 1)).isPresent());
            seen.add(table.find(DocId.of(1)).orElseThrow().atEnd());
            seen.add(
                Assertions.assertThrows(
                    DocNotFoundError.class, () -> table.open(DocId.of(113)),
                    "Absent"
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of(
                TermListTableTest.DOCS, true, false, false, false,
                "No term list for document 113"
            ),
            seen,
            "112 empty term lists"
        );
    }

    @Test
    void writesReferenceFixtureByteForByte()
        throws URISyntaxException, IOException {
        final MemoryBlocks blocks =
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(blocks, TermListTableTest.COMPRESS);
        final TermListWriter writer = new TermListWriter(table);
        for (int doc = 1; doc <= TermListTableTest.DOCS; ++doc) {
            writer.set(DocId.of(doc), Map.of());
        }
        table.commit(new RevisionNumber(1L));
        Assertions.assertArrayEquals(
            Files.readAllBytes(TermListTableTest.path("termlist")),
            blocks.bytes(0L),
            "Block as the reference wrote it"
        );
    }

    @Test
    void setsReplacesAndDeletes() {
        final MemoryBlocks blocks =
            new MemoryBlocks(new HashMap<>(), BlockSize.MIN);
        final TableWriter table =
            TableWriter.create(blocks, TermListTableTest.COMPRESS);
        final TermListWriter writer = new TermListWriter(table);
        final List<Object> seen = new ArrayList<>(8);
        writer.set(DocId.of(1), Map.of("a", new TermCount(1)));
        writer.set(DocId.of(2), Map.of("b", new TermCount(2)));
        seen.add(TermListTableTest.hex(table, 1));
        writer.set(DocId.of(1), Map.of("c", new TermCount(3)));
        seen.add(TermListTableTest.hex(table, 1));
        seen.add(writer.delete(DocId.of(2)));
        seen.add(writer.delete(DocId.of(2)));
        seen.add(table.entries());
        final VidrioTable committed = VidrioTable.over(
            blocks, table.commit(new RevisionNumber(1L)).root()
        );
        try (committed) {
            final TermListTable lists =
                TermListTable.of(committed, PostingTable.of(Readback.crafted()));
            seen.add(Documents.read(lists.open(DocId.of(1))));
            seen.add(lists.contains(DocId.of(2)));
        }
        Assertions.assertEquals(
            List.of(
                "0101016101", "0301016303", true, false, new EntryCount(1L),
                List.of("c:3:0"), false
            ),
            seen,
            "Set replaces, delete reports presence"
        );
    }

    private static String hex(final TableWriter table, final int doc) {
        return HexFormat.of().formatHex(
            table.get(TermListKeys.of(DocId.of(doc))).orElseThrow()
        );
    }

    private static VidrioTable fixture(final String name)
        throws URISyntaxException {
        return VidrioTable.open(
            TermListTableTest.path(name),
            new TableRoot(
                new BlockNumber(0L), 0, BlockSize.MIN, new RevisionNumber(1L),
                false
            )
        );
    }

    private static Path path(final String name) throws URISyntaxException {
        return Path.of(
            TermListTableTest.class.getResource(
                String.format("oversized/%s.bin", name)
            ).toURI()
        );
    }
}
