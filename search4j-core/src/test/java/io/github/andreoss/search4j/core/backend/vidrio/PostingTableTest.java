/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PostingTable}.
 *
 * @since 0.1.0
 */
final class PostingTableTest {

    @Test
    void readsStatsOfTerms() {
        final Corpus corpus = Corpus.of(1L, BlockSize.MIN, 1);
        corpus.add(
            DocId.of(1),
            Map.of("one", new TermCount(7), "zero", TermCount.ZERO, "many", new TermCount(2))
        );
        corpus.add(DocId.of(2), Map.of("zero", TermCount.ZERO, "many", new TermCount(9)));
        corpus.add(DocId.of(3), Map.of("many", new TermCount(1)));
        final List<Object> seen = new ArrayList<>(6);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            final PostingTable lookup = PostingTable.of(table);
            seen.add(lookup.stats("one"));
            seen.add(lookup.stats("zero"));
            seen.add(lookup.stats("many"));
            seen.add(lookup.stats("none"));
            seen.add(lookup.hasTerm("many"));
            seen.add(lookup.hasTerm("none"));
        }
        Assertions.assertEquals(
            List.of(
                new TermStats(
                    new TermFrequencies(new TermCount(1), new TermCount(7)),
                    new TermCount(7)
                ),
                new TermStats(
                    new TermFrequencies(new TermCount(2), TermCount.ZERO),
                    TermCount.ZERO
                ),
                new TermStats(
                    new TermFrequencies(new TermCount(3), new TermCount(12)),
                    new TermCount(10)
                ),
                TermStats.NONE, true, false
            ),
            seen,
            "Bound from the collection frequency and the first posting"
        );
    }

    @Test
    void looksUpDocumentLengthsInAnyOrder() {
        final Corpus corpus = Corpus.of(2L, BlockSize.MIN, 1);
        corpus.add(DocId.of(1), Map.of("x", new TermCount(3)));
        corpus.add(DocId.of(5), Map.of());
        corpus.add(DocId.of(9), Map.of("x", new TermCount(4)));
        final List<Object> seen = new ArrayList<>(7);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            final PostingTable lookup = PostingTable.of(table);
            seen.add(lookup.docLength(DocId.of(9)));
            seen.add(lookup.docLength(DocId.of(1)));
            seen.add(lookup.docLength(DocId.of(5)));
            seen.add(lookup.hasDocument(DocId.of(4)));
            seen.add(lookup.hasDocument(DocId.of(10)));
            seen.add(lookup.hasDocument(DocId.of(9)));
            seen.add(
                Assertions.assertThrows(
                    DocNotFoundError.class, () -> lookup.docLength(DocId.of(6)),
                    "Absent document"
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of(
                new TermCount(4), new TermCount(3), TermCount.ZERO, false,
                false, true, "Document 6 not found"
            ),
            seen,
            "Backwards, forwards and past the end"
        );
    }

    @Test
    void reportsDocIdRangeOfEmptyTable() {
        final Corpus corpus = Corpus.of(3L, BlockSize.MIN, 1);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            final PostingTable lookup = PostingTable.of(table);
            Assertions.assertEquals(
                List.of(
                    new DocRange(DocId.NONE, DocId.NONE), false, false,
                    TermStats.NONE
                ),
                List.of(
                    lookup.docIdRange(), lookup.hasDocument(DocId.of(1)),
                    lookup.hasTerm(""), lookup.stats("")
                ),
                "Nothing in use"
            );
        }
    }

    @Test
    void reportsDocIdRangeAcrossChunks() {
        final Corpus corpus = Corpus.of(4L, BlockSize.MIN, 1);
        for (int doc = 3; doc <= 3000; ++doc) {
            corpus.add(DocId.of(doc), Map.of());
        }
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            seen.add(PostingTable.of(table).docIdRange());
            seen.add(Readback.chunks(table, "").size() >= 2);
        }
        for (int doc = 2990; doc <= 3000; ++doc) {
            corpus.delete(DocId.of(doc));
        }
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(PostingTable.of(table).docIdRange());
        }
        Assertions.assertEquals(
            List.of(
                new DocRange(DocId.of(3), DocId.of(3000)), true,
                new DocRange(DocId.of(3), DocId.of(2989))
            ),
            seen,
            "First of the first chunk to last of the last chunk"
        );
    }

    @Test
    void rejectsDamagedLastLengthChunk() {
        final VidrioTable table = Readback.crafted(
            "00e0", "0000003002020104", "00e0feffffffffffffffff", "3101070001"
        );
        try (table) {
            Assertions.assertEquals(
                "Document number out of range in key 00e0feffffffffffffffff",
                Assertions.assertThrows(
                    DatabaseCorruptError.class, PostingTable.of(table)::docIdRange,
                    "Key past the document range"
                ).getMessage(),
                "Message"
            );
        }
    }
}
