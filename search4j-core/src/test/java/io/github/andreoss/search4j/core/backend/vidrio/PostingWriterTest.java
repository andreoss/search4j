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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PostingWriter}, {@link ChunkWriter} and
 * {@link PostingChanges}: merges read back through the lists.
 *
 * @since 0.1.0
 */
final class PostingWriterTest {

    /**
     * Term of the lists.
     */
    private static final String TERM = "a";

    /**
     * Number of documents of a long list.
     */
    private static final int LONG = 2500;

    @Test
    void createsListWithFrequencies() {
        final Corpus corpus = Corpus.of(1L, BlockSize.MIN, 1);
        corpus.add(DocId.of(1), Map.of(PostingWriterTest.TERM, new TermCount(2)));
        corpus.add(DocId.of(3), Map.of(PostingWriterTest.TERM, new TermCount(4)));
        final List<Object> seen = new ArrayList<>(4);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            seen.add(Readback.postings(table, PostingWriterTest.TERM));
            seen.add(PostingTable.of(table).stats(PostingWriterTest.TERM));
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(
                HexFormat.of().formatHex(
                    table.get(PostListKeys.first(PostListKeys.bytes("a")))
                        .orElseThrow()
                )
            );
        }
        Assertions.assertEquals(
            List.of(
                List.of("1:2", "3:4"),
                new TermStats(
                    new TermFrequencies(new TermCount(2), new TermCount(6)),
                    new TermCount(4)
                ),
                List.of(), "0206003102020104"
            ),
            seen,
            "One chunk encoded as the reference"
        );
    }

    @Test
    void appendsInsertsModifiesAndDeletes() {
        final Corpus corpus = Corpus.of(2L, BlockSize.MIN, 1);
        corpus.add(DocId.of(1), Map.of(PostingWriterTest.TERM, new TermCount(2)));
        corpus.add(DocId.of(3), Map.of(PostingWriterTest.TERM, new TermCount(4)));
        corpus.add(DocId.of(10), Map.of(PostingWriterTest.TERM, new TermCount(1)));
        corpus.commit(1L);
        corpus.replace(DocId.of(3), Map.of(PostingWriterTest.TERM, new TermCount(5)));
        corpus.delete(DocId.of(10));
        corpus.add(DocId.of(20), Map.of(PostingWriterTest.TERM, new TermCount(3)));
        corpus.add(DocId.of(2), Map.of(PostingWriterTest.TERM, new TermCount(1)));
        corpus.add(DocId.of(30), Map.of("b", new TermCount(3)));
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.postings(table, PostingWriterTest.TERM));
            seen.add(PostingTable.of(table).stats(PostingWriterTest.TERM).freqs());
            seen.add(Readback.lengths(table));
        }
        Assertions.assertEquals(
            List.of(
                List.of("1:2", "2:1", "3:5", "20:3"),
                new TermFrequencies(new TermCount(4), new TermCount(11)),
                List.of("1:2", "2:1", "3:5", "20:3", "30:3")
            ),
            seen,
            "Second revision merged into the first"
        );
    }

    @Test
    void splitsLongListIntoChunks() {
        final Corpus corpus = PostingWriterTest.lengthy(3L);
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            seen.add(Readback.chunks(table, PostingWriterTest.TERM).size() >= 2);
            seen.add(
                Readback.postings(table, PostingWriterTest.TERM)
                    .equals(corpus.expected(PostingWriterTest.TERM))
            );
            seen.add(PostingTable.of(table).stats(PostingWriterTest.TERM).freqs());
        }
        Assertions.assertEquals(
            List.of(
                true, true,
                new TermFrequencies(
                    new TermCount(PostingWriterTest.LONG),
                    new TermCount(PostingWriterTest.LONG)
                )
            ),
            seen,
            "Chunks of about 2000 bytes"
        );
    }

    @Test
    void dropsSingleChunkListWhenEmptied() {
        final Corpus corpus = Corpus.of(4L, BlockSize.MIN, 1);
        corpus.add(DocId.of(1), Map.of(PostingWriterTest.TERM, new TermCount(2)));
        corpus.add(DocId.of(3), Map.of("b", new TermCount(4)));
        corpus.commit(1L);
        corpus.delete(DocId.of(1));
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            final PostingTable lookup = PostingTable.of(table);
            seen.add(lookup.hasTerm(PostingWriterTest.TERM));
            seen.add(lookup.stats(PostingWriterTest.TERM));
            seen.add(Readback.postings(table, "b"));
        }
        Assertions.assertEquals(
            List.of(false, TermStats.NONE, List.of("3:4")), seen,
            "Key gone, the other term kept"
        );
    }

    @Test
    void dropsEveryChunkWhenEmptied() {
        final Corpus corpus = PostingWriterTest.lengthy(5L);
        corpus.add(DocId.of(9000), Map.of("b", new TermCount(1)));
        corpus.commit(1L);
        for (int doc = 1; doc <= PostingWriterTest.LONG; ++doc) {
            corpus.delete(DocId.of(doc));
        }
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(PostingTable.of(table).hasTerm(PostingWriterTest.TERM));
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(Readback.postings(table, "b"));
        }
        Assertions.assertEquals(
            List.of(false, List.of(), List.of("9000:1")), seen,
            "Every chunk key gone"
        );
    }

    @Test
    void promotesSecondChunkWhenFirstEmptied() {
        final Corpus corpus = PostingWriterTest.lengthy(6L);
        final List<DocId> before;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            before = Readback.chunks(table, PostingWriterTest.TERM);
        }
        for (int doc = 1; doc < before.get(0).value(); ++doc) {
            corpus.delete(DocId.of(doc));
        }
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(
                Readback.postings(table, PostingWriterTest.TERM)
                    .equals(corpus.expected(PostingWriterTest.TERM))
            );
            final VidrioPostList list =
                VidrioPostList.open(table, Positions.none(), PostingWriterTest.TERM);
            list.next();
            seen.add(list.docId());
        }
        Assertions.assertEquals(
            List.of(before.subList(1, before.size()), true, before.get(0)),
            seen,
            "Second chunk filed under the first key"
        );
    }

    @Test
    void terminatesPreviousChunkWhenLastEmptied() {
        final Corpus corpus = PostingWriterTest.lengthy(7L);
        final List<DocId> before;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            before = Readback.chunks(table, PostingWriterTest.TERM);
        }
        final DocId last = before.get(before.size() - 1);
        for (int doc = last.value(); doc <= PostingWriterTest.LONG; ++doc) {
            corpus.delete(DocId.of(doc));
        }
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(Readback.postings(table, PostingWriterTest.TERM));
            final VidrioPostList list =
                VidrioPostList.open(table, Positions.none(), PostingWriterTest.TERM);
            list.skipTo(DocId.of(last.value() - 1));
            seen.add(
                list.docIdRange(new DocRange(DocId.of(1), DocId.of(9999)))
            );
        }
        Assertions.assertEquals(
            List.of(
                before.subList(0, before.size() - 1),
                corpus.expected(PostingWriterTest.TERM),
                new DocRange(
                    PostingWriterTest.start(before), DocId.of(last.value() - 1)
                )
            ),
            seen,
            "Previous chunk marked last"
        );
    }

    @Test
    void removesMiddleChunk() {
        final Corpus corpus = Corpus.of(8L, BlockSize.MIN, 1);
        for (int doc = 1; doc <= 4000; ++doc) {
            corpus.add(DocId.of(doc), Map.of(PostingWriterTest.TERM, new TermCount(1)));
        }
        final List<DocId> before;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            before = Readback.chunks(table, PostingWriterTest.TERM);
        }
        for (int doc = before.get(0).value(); doc < before.get(1).value(); ++doc) {
            corpus.delete(DocId.of(doc));
        }
        final List<Object> seen = new ArrayList<>(2);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(
                Readback.postings(table, PostingWriterTest.TERM)
                    .equals(corpus.expected(PostingWriterTest.TERM))
            );
        }
        final List<DocId> after = new ArrayList<>(before);
        after.remove(0);
        Assertions.assertEquals(
            List.of(after, true), seen, "Middle chunk key gone"
        );
    }

    @Test
    void renamesChunkWhoseFirstDocumentChanges() {
        final Corpus corpus = PostingWriterTest.lengthy(9L);
        final List<DocId> before;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            before = Readback.chunks(table, PostingWriterTest.TERM);
        }
        corpus.delete(before.get(0));
        corpus.delete(before.get(0).next());
        final List<Object> seen = new ArrayList<>(2);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.chunks(table, PostingWriterTest.TERM).get(0));
            seen.add(
                Readback.postings(table, PostingWriterTest.TERM)
                    .equals(corpus.expected(PostingWriterTest.TERM))
            );
        }
        Assertions.assertEquals(
            List.of(DocId.of(before.get(0).value() + 2), true), seen,
            "Chunk filed under its new first document"
        );
    }

    @Test
    void appendsPastEndOfChunk() {
        final Corpus corpus = Corpus.of(18L, BlockSize.MIN, 1);
        corpus.add(DocId.of(1), Map.of(PostingWriterTest.TERM, new TermCount(2)));
        corpus.add(DocId.of(3), Map.of(PostingWriterTest.TERM, new TermCount(4)));
        corpus.commit(1L);
        corpus.add(DocId.of(10), Map.of(PostingWriterTest.TERM, new TermCount(1)));
        final List<Object> seen = new ArrayList<>(2);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.postings(table, PostingWriterTest.TERM));
            seen.add(
                HexFormat.of().formatHex(
                    table.get(PostListKeys.first(PostListKeys.bytes("a")))
                        .orElseThrow()
                )
            );
        }
        Assertions.assertEquals(
            List.of(List.of("1:2", "3:4", "10:1"), "03070031090201040601"),
            seen,
            "Postings of the chunk taken over as encoded"
        );
    }

    @Test
    void terminatesFirstChunkWhenSecondEmptied() {
        final Corpus corpus = Corpus.of(19L, BlockSize.MIN, 1);
        for (int doc = 1; doc <= 1500; ++doc) {
            corpus.add(DocId.of(doc), Map.of(PostingWriterTest.TERM, new TermCount(1)));
        }
        final List<DocId> before;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            before = Readback.chunks(table, PostingWriterTest.TERM);
        }
        for (int doc = before.get(0).value(); doc <= 1500; ++doc) {
            corpus.delete(DocId.of(doc));
        }
        final List<Object> seen = new ArrayList<>(4);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(before.size());
            seen.add(Readback.chunks(table, PostingWriterTest.TERM));
            seen.add(
                Readback.postings(table, PostingWriterTest.TERM)
                    .equals(corpus.expected(PostingWriterTest.TERM))
            );
            seen.add(PostingTable.of(table).stats(PostingWriterTest.TERM).freqs());
        }
        Assertions.assertEquals(
            List.of(
                1, List.of(), true,
                new TermFrequencies(
                    new TermCount(before.get(0).value() - 1),
                    new TermCount(before.get(0).value() - 1)
                )
            ),
            seen,
            "First chunk marked last, frequencies kept"
        );
    }

    @Test
    void createsAndRemovesLengths() {
        final Corpus corpus = Corpus.of(10L, BlockSize.MIN, 1);
        corpus.add(DocId.of(5), Map.of("x", new TermCount(3)));
        corpus.add(DocId.of(9), Map.of());
        final List<Object> seen = new ArrayList<>(5);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            seen.add(Readback.lengths(table));
            seen.add(PostingTable.of(table).docIdRange());
        }
        corpus.delete(DocId.of(5));
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            seen.add(Readback.lengths(table));
        }
        corpus.delete(DocId.of(9));
        try (VidrioTable table = corpus.table(corpus.commit(3L))) {
            seen.add(PostingTable.of(table).docIdRange());
            seen.add(table.contains(PostListKeys.first(new byte[0])));
        }
        Assertions.assertEquals(
            List.of(
                List.of("5:3", "9:0"), new DocRange(DocId.of(5), DocId.of(9)),
                List.of("9:0"), new DocRange(DocId.NONE, DocId.NONE), false
            ),
            seen,
            "Lengths list created, shrunk and removed"
        );
    }

    @Test
    void removingAbsentLengthLeavesNoList() {
        final Corpus corpus = Corpus.of(11L, BlockSize.MIN, 1);
        final TableWriter writer = corpus.writer();
        final PostingChanges lengths = new PostingChanges();
        lengths.remove(DocId.of(7), TermCount.ZERO);
        new PostingWriter(writer).mergeLengths(lengths);
        new PostingWriter(writer).mergeLengths(new PostingChanges());
        Assertions.assertEquals(
            List.of(0L, false),
            List.of(
                writer.entries().value(),
                writer.contains(PostListKeys.first(new byte[0]))
            ),
            "Placeholder first chunk removed again"
        );
    }

    @Test
    void rejectsEmptyTermChanges() {
        final PostingWriter writer =
            new PostingWriter(Corpus.of(12L, BlockSize.MIN, 1).writer());
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> writer.merge(PostingWriterTest.TERM, new PostingChanges()),
            "No posting to merge"
        );
    }

    @Test
    void rejectsFrequencyBelowZero() {
        final PostingWriter writer =
            new PostingWriter(Corpus.of(13L, BlockSize.MIN, 1).writer());
        final PostingChanges changes = new PostingChanges();
        changes.remove(DocId.of(1), new TermCount(2));
        Assertions.assertEquals(
            "Term frequency out of range: -1",
            Assertions.assertThrows(
                InvariantError.class,
                () -> writer.merge(PostingWriterTest.TERM, changes),
                "Removal from an absent list"
            ).getMessage(),
            "Message"
        );
    }

    @Test
    void wrapsCollectionFrequencyAsTheReference() {
        final Corpus corpus = Corpus.of(14L, BlockSize.MIN, 1);
        final TermCount wdf = TermCount.parse("3000000000");
        corpus.add(DocId.of(1), Map.of(PostingWriterTest.TERM, wdf));
        corpus.add(DocId.of(2), Map.of(PostingWriterTest.TERM, wdf));
        final List<Object> seen = new ArrayList<>(3);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            seen.add(Readback.postings(table, PostingWriterTest.TERM));
            seen.add(PostingTable.of(table).stats(PostingWriterTest.TERM));
            seen.add(
                HexFormat.of().formatHex(
                    table.get(PostListKeys.first(PostListKeys.bytes("a")))
                        .orElseThrow()
                )
            );
        }
        Assertions.assertEquals(
            List.of(
                List.of("1:3000000000", "2:3000000000"),
                new TermStats(
                    new TermFrequencies(
                        new TermCount(2), TermCount.parse("1705032704")
                    ),
                    wdf
                ),
                "0280f882ad0600310180bcc1960b0080bcc1960b"
            ),
            seen,
            "Wdf above the signed range, collection frequency modulo 2^32"
        );
    }

    @Test
    void tracksDeltasOfChanges() {
        final PostingChanges changes = new PostingChanges();
        changes.add(DocId.of(4), new TermCount(3));
        changes.update(DocId.of(2), new TermCount(5), new TermCount(1));
        changes.remove(DocId.of(1), new TermCount(2));
        changes.add(DocId.of(1), new TermCount(7));
        Assertions.assertEquals(
            List.of(1L, 4L, "[1, 2, 4]", "[Optional[7], Optional[1], Optional[3]]"),
            List.of(
                changes.termDelta(), changes.collectionDelta(),
                changes.entries().keySet().toString(),
                changes.entries().values().toString()
            ),
            "Deltas summed, last change per document kept"
        );
    }

    @Test
    void rejectsPostingsOutOfOrder() {
        final ChunkWriter writer = new ChunkWriter(
            Corpus.of(15L, BlockSize.MIN, 1).writer(), PostListKeys.bytes("a"),
            new byte[0], true, true
        );
        writer.append(DocId.of(5), new TermCount(1));
        Assertions.assertThrows(
            InvariantError.class,
            () -> writer.append(DocId.of(5), new TermCount(1)),
            "Same document twice"
        );
    }

    @Test
    void rejectsWritesWithoutFirstChunk() {
        final Corpus corpus = Corpus.of(16L, BlockSize.MIN, 1);
        final TableWriter table = corpus.writer();
        final byte[] term = PostListKeys.bytes("a");
        final byte[] first = PostListKeys.first(term);
        final List<ChunkWriter> cases = List.of(
            new ChunkWriter(table, term, first, true, true),
            new ChunkWriter(
                table, term,
                PostListKeys.chunk(PostListKeys.bytes("b"), DocId.of(2)),
                false, true
            ),
            new ChunkWriter(table, term, first, true, false)
        );
        final List<String> seen = new ArrayList<>(cases.size());
        for (final ChunkWriter writer : cases) {
            if (seen.size() < 2) {
                writer.append(DocId.of(1), new TermCount(1));
            }
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class, writer::flush, "Empty table"
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of(
                "First chunk of the posting list has disappeared",
                "Have invalid key writing to posting list",
                "The key we're working on has disappeared"
            ),
            seen,
            "Writes over missing chunks rejected"
        );
    }

    @Test
    void rejectsWritesOnDamagedLists() {
        final Corpus corpus = Corpus.of(16L, BlockSize.MIN, 1);
        final TableWriter table = corpus.writer();
        final byte[] term = PostListKeys.bytes("a");
        final byte[] first = PostListKeys.first(term);
        table.add(first, HexFormat.of().parseHex("0206003002020104"));
        final List<String> seen = new ArrayList<>(3);
        seen.add(
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                new ChunkWriter(table, term, first, true, false)::flush,
                "No next chunk"
            ).getMessage()
        );
        table.add(
            PostListKeys.first(PostListKeys.bytes("b")),
            HexFormat.of().parseHex("010300310003")
        );
        seen.add(
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                new ChunkWriter(table, term, first, true, false)::flush,
                "Next chunk of another term"
            ).getMessage()
        );
        final byte[] other = PostListKeys.bytes("c");
        seen.add(
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                new ChunkWriter(
                    table, other, PostListKeys.chunk(other, DocId.of(2)),
                    false, true
                )::flush,
                "No previous chunk"
            ).getMessage()
        );
        Assertions.assertEquals(
            List.of(
                "Expected another key but found none",
                "Expected another key with the same term name but found a different one",
                "Couldn't find chunk before deleted chunk"
            ),
            seen,
            "Every inconsistency of the table rejected"
        );
    }

    @Test
    void rejectsRelocationIntoVanishedList() {
        final TableWriter table = Corpus.of(17L, BlockSize.MIN, 1).writer();
        table.add(
            PostListKeys.first(PostListKeys.bytes("a")),
            HexFormat.of().parseHex("0206003002020104")
        );
        table.add(
            PostListKeys.first(PostListKeys.bytes("b")),
            HexFormat.of().parseHex("010300310003")
        );
        final PostingChanges changes = new PostingChanges();
        changes.add(DocId.of(9), new TermCount(1));
        Assertions.assertEquals(
            "Expected another key with the same term name but found a different one",
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> new PostingWriter(table).merge("a", changes),
                "Continuation chunk missing"
            ).getMessage(),
            "Message"
        );
    }

    private static Corpus lengthy(final long seed) {
        final Corpus corpus = Corpus.of(seed, BlockSize.MIN, 1);
        for (int doc = 1; doc <= PostingWriterTest.LONG; ++doc) {
            corpus.add(
                DocId.of(doc), Map.of(PostingWriterTest.TERM, new TermCount(1))
            );
        }
        return corpus;
    }

    private static DocId start(final List<DocId> chunks) {
        DocId result = DocId.of(1);
        if (chunks.size() > 1) {
            result = chunks.get(chunks.size() - 2);
        }
        return result;
    }
}
