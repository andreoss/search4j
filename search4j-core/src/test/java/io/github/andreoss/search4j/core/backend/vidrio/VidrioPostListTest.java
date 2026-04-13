/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioPostList} and {@link VidrioAllDocsPostList}:
 * the reference fixture and tables built through the merge.
 *
 * @since 0.1.0
 */
final class VidrioPostListTest {

    /**
     * Number of documents of the random corpus.
     */
    private static final int DOCS = 3000;

    /**
     * Number of random terms per document.
     */
    private static final int PER_DOC = 4;

    /**
     * Term of the handcrafted lists.
     */
    private static final String TERM = "a";

    /**
     * Key of the handcrafted lists, in hexadecimal.
     */
    private static final String KEY = "61";

    /**
     * One chunk of documents 1 and 3, in hexadecimal.
     */
    private static final String SINGLE = "0206003102020104";

    /**
     * Range of every document of a table.
     */
    private static final DocRange WHOLE =
        new DocRange(DocId.of(1), DocId.of(Integer.MAX_VALUE));

    @Test
    void readsReferenceFixtureLengths() throws URISyntaxException {
        final List<Object> seen = new ArrayList<>(6);
        try (VidrioTable table = VidrioPostListTest.fixture()) {
            final TableCursor cursor = table.cursor();
            cursor.rewind();
            cursor.next();
            seen.add(HexFormat.of().formatHex(cursor.tag()));
            final PostingTable lookup = PostingTable.of(table);
            seen.add(lookup.docIdRange());
            seen.add(lookup.hasDocument(DocId.of(113)));
            seen.add(lookup.docLength(DocId.of(112)));
            seen.add(lookup.docLength(DocId.of(1)));
            seen.add(Readback.lengths(table).size());
        }
        Assertions.assertEquals(
            List.of(
                String.format("000000316f00%s", "0000".repeat(111)),
                new DocRange(DocId.of(1), DocId.of(112)), false,
                TermCount.ZERO, TermCount.ZERO, 112
            ),
            seen,
            "112 documents of length 0 in one chunk"
        );
    }

    @Test
    void iteratesReferenceFixtureDocuments() throws URISyntaxException {
        final List<Object> seen = new ArrayList<>(114);
        try (VidrioTable table = VidrioPostListTest.fixture()) {
            final VidrioAllDocsPostList list =
                VidrioAllDocsPostList.open(table, new TermCount(112));
            seen.add(list.termFrequency());
            list.next();
            while (!list.atEnd()) {
                seen.add(list.docId().value());
                seen.add(list.wdf().value());
                list.next();
            }
        }
        final List<Object> expected = new ArrayList<>(114);
        expected.add(new TermCount(112));
        for (int doc = 1; doc <= 112; ++doc) {
            expected.add(doc);
            expected.add(1L);
        }
        Assertions.assertEquals(expected, seen, "Every document, wdf 1");
    }

    @Test
    void readsRandomCorpusByTermAndDocument() {
        final Corpus corpus = VidrioPostListTest.corpus(1L);
        final TableState state = corpus.commit(1L);
        final List<String> faults = new ArrayList<>(0);
        try (VidrioTable table = corpus.table(state)) {
            faults.addAll(VidrioPostListTest.check(corpus, table));
            if (Readback.chunks(table, Corpus.EVERY).size() < 2) {
                faults.add("one chunk only");
            }
            if (state.root().level() < 1) {
                faults.add("one block only");
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Every term and every length as the model"
        );
    }

    @Test
    void skipsAtAndPastChunkBoundaries() {
        final Corpus corpus = VidrioPostListTest.corpus(2L);
        final List<String> faults = new ArrayList<>(0);
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            final List<DocId> bounds = Readback.chunks(table, Corpus.EVERY);
            for (final DocId bound : bounds) {
                for (int delta = -1; delta <= 1; ++delta) {
                    final DocId target = DocId.of(bound.value() + delta);
                    final VidrioPostList list =
                        VidrioPostList.open(table, Positions.none(), Corpus.EVERY);
                    list.skipTo(target);
                    if (!list.docId().equals(target)) {
                        faults.add(String.format("%s -> %s", target, list.docId()));
                    }
                }
            }
            final VidrioPostList list = VidrioPostList.open(table, Positions.none(), Corpus.EVERY);
            for (final DocId bound : bounds) {
                list.skipTo(bound);
                list.next();
                if (list.docId().value() != bound.value() + 1) {
                    faults.add(String.format("after %s: %s", bound, list.docId()));
                }
            }
            list.skipTo(DocId.of(VidrioPostListTest.DOCS + 1));
            if (!list.atEnd()) {
                faults.add("not at end");
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Skips land on the boundary documents"
        );
    }

    @Test
    void skipsOverGapBetweenChunks() {
        final Corpus corpus = VidrioPostListTest.corpus(3L);
        final DocId bound;
        try (VidrioTable table = corpus.table(corpus.commit(1L))) {
            bound = Readback.chunks(table, Corpus.EVERY).get(0);
        }
        for (int delta = -3; delta <= 2; ++delta) {
            corpus.delete(DocId.of(bound.value() + delta));
        }
        final List<Object> seen = new ArrayList<>(4);
        try (VidrioTable table = corpus.table(corpus.commit(2L))) {
            final VidrioPostList list = VidrioPostList.open(table, Positions.none(), Corpus.EVERY);
            list.skipTo(DocId.of(bound.value() - 2));
            seen.add(list.docId().value() - bound.value());
            seen.add(list.docIdRange(VidrioPostListTest.WHOLE).first());
            final VidrioPostList other =
                VidrioPostList.open(table, Positions.none(), Corpus.EVERY);
            other.skipTo(bound);
            seen.add(other.docId().value() - bound.value());
            seen.add(
                Readback.postings(table, Corpus.EVERY)
                    .equals(corpus.expected(Corpus.EVERY))
            );
        }
        Assertions.assertEquals(
            List.of(3, DocId.of(bound.value() + 3), 3, true), seen,
            "Target between chunks lands on the next chunk"
        );
    }

    @Test
    void keepsPostingsAcrossRevisions() {
        final Corpus corpus = VidrioPostListTest.corpus(4L);
        final Random random = new Random(4L);
        final List<String> faults = new ArrayList<>(0);
        corpus.commit(1L);
        for (int rev = 2; rev <= 4; ++rev) {
            VidrioPostListTest.churn(corpus, random, rev);
            try (VidrioTable table = corpus.table(corpus.commit(rev))) {
                for (final String fault : VidrioPostListTest.check(corpus, table)) {
                    faults.add(String.format("rev %d: %s", rev, fault));
                }
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Deletions, changes and additions kept"
        );
    }

    @Test
    void refusesDocumentBeforeStartAndAfterEnd() {
        final List<Object> seen = new ArrayList<>(6);
        final VidrioTable table = Readback.crafted(
            VidrioPostListTest.KEY, VidrioPostListTest.SINGLE
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, Positions.none(), VidrioPostListTest.TERM);
            seen.add(VidrioPostListTest.failure(list::docId));
            seen.add(VidrioPostListTest.failure(list::wdf));
            seen.add(VidrioPostListTest.failure(list::openPositionList));
            list.next();
            list.next();
            list.next();
            seen.add(list.atEnd());
            seen.add(VidrioPostListTest.failure(list::docId));
            seen.add(VidrioPostListTest.failure(list::wdf));
        }
        Assertions.assertEquals(
            List.of(
                InvalidOperationError.class, InvalidOperationError.class,
                InvalidOperationError.class, true,
                InvalidOperationError.class, InvalidOperationError.class
            ),
            seen,
            "No document before the first move and after the end"
        );
    }

    @Test
    void handlesAbsentTerm() {
        final List<Object> seen = new ArrayList<>(12);
        final VidrioTable table = Readback.crafted(
            VidrioPostListTest.KEY, VidrioPostListTest.SINGLE
        );
        try (table) {
            final VidrioPostList list = VidrioPostList.open(table, Positions.none(), "b");
            seen.add(list.termFrequency());
            seen.add(list.collectionFrequency());
            seen.add(list.wdfUpperBound());
            seen.add(list.atEnd());
            seen.add(list.docIdRange(VidrioPostListTest.WHOLE));
            seen.add(list.jumpTo(DocId.of(1)));
            list.next();
            seen.add(list.atEnd());
            list.skipTo(DocId.of(1));
            seen.add(list.description());
            final VidrioPostList other =
                VidrioPostList.open(table, Positions.none(), VidrioPostListTest.TERM);
            seen.add(other.jumpTo(DocId.of(3)));
            seen.add(other.jumpTo(DocId.of(2)));
            seen.add(other.jumpTo(DocId.of(1)));
            seen.add(other.docId());
        }
        Assertions.assertEquals(
            List.of(
                TermCount.ZERO, TermCount.ZERO, TermCount.ZERO, true,
                new DocRange(DocId.of(1), DocId.NONE), false, true, "b:0",
                true, false, true, DocId.of(1)
            ),
            seen,
            "Empty list at the end from the start"
        );
    }

    @Test
    void describesAndOpensNearby() {
        final List<Object> seen = new ArrayList<>(7);
        final VidrioTable table = Readback.crafted(
            VidrioPostListTest.KEY, VidrioPostListTest.SINGLE,
            "62", "010300310003"
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, Positions.none(), VidrioPostListTest.TERM);
            seen.add(list.description());
            seen.add(list.openNearby("", true).isPresent());
            final LeafPostList near = list.openNearby("b", false).orElseThrow();
            seen.add(near.term());
            seen.add(near.termFrequency());
            seen.add(list.openNearby("c", false).orElseThrow().termFrequency());
            list.next();
            seen.add(list.readPositionList().approxSize());
            seen.add(list.openPositionList().next());
        }
        Assertions.assertEquals(
            List.of(
                "a:2", false, "b", new TermCount(1), TermCount.ZERO,
                TermCount.ZERO, false
            ),
            seen,
            "Nearby lists share the table, positions empty"
        );
    }

    @Test
    void narrowsDocIdRangeToChunk() {
        final List<Object> seen = new ArrayList<>(3);
        final VidrioTable table = Readback.crafted(
            VidrioPostListTest.KEY, "0309003002020104", "61000005", "3101070001"
        );
        try (table) {
            final VidrioPostList list =
                VidrioPostList.open(table, Positions.none(), VidrioPostListTest.TERM);
            seen.add(list.docIdRange(VidrioPostListTest.WHOLE));
            list.skipTo(DocId.of(5));
            seen.add(list.docIdRange(VidrioPostListTest.WHOLE));
            seen.add(Readback.postings(table, VidrioPostListTest.TERM));
        }
        Assertions.assertEquals(
            List.of(
                new DocRange(DocId.of(1), DocId.of(Integer.MAX_VALUE)),
                new DocRange(DocId.of(5), DocId.of(6)),
                List.of("1:2", "3:4", "5:7", "6:1")
            ),
            seen,
            "Last chunk bounds the range"
        );
    }

    @Test
    void iteratesEveryDocumentWithFrequencyOne() {
        final List<Object> seen = new ArrayList<>(10);
        final VidrioTable table = Readback.crafted("00e0", "0000003104030302");
        try (table) {
            final VidrioAllDocsPostList list =
                VidrioAllDocsPostList.open(table, new TermCount(2));
            seen.add(list.description());
            seen.add(list.collectionFrequency());
            seen.add(list.wdfUpperBound());
            seen.add(list.openNearby("x", false).isPresent());
            seen.add(VidrioPostListTest.failure(list::readPositionList));
            seen.add(VidrioPostListTest.failure(list::wdf));
            list.skipTo(DocId.of(2));
            seen.add(list.docId());
            seen.add(list.wdf());
            seen.add(list.docIdRange(VidrioPostListTest.WHOLE));
            list.next();
            seen.add(list.atEnd());
        }
        Assertions.assertEquals(
            List.of(
                "VidrioAllDocsPostList(doccount=2)", new TermCount(2),
                new TermCount(1), false, InvalidOperationError.class,
                InvalidOperationError.class, DocId.of(5), new TermCount(1),
                new DocRange(DocId.of(1), DocId.of(5)), true
            ),
            seen,
            "Documents 1 and 5, no positions"
        );
    }

    private static Corpus corpus(final long seed) {
        final Corpus corpus = Corpus.of(seed, BlockSize.MIN, 12);
        for (int doc = 1; doc <= VidrioPostListTest.DOCS; ++doc) {
            corpus.addRandom(DocId.of(doc), VidrioPostListTest.PER_DOC);
        }
        return corpus;
    }

    private static void churn(
        final Corpus corpus, final Random random, final int rev
    ) {
        for (final DocId doc : corpus.docs()) {
            final int kind = random.nextInt(10);
            if (kind < 3) {
                corpus.delete(doc);
            } else if (kind < 5) {
                final Map<String, TermCount> terms =
                    new TreeMap<>(corpus.terms(doc));
                terms.put(Corpus.EVERY, new TermCount(rev));
                terms.remove(String.format("w%d", random.nextInt(12)));
                terms.put(
                    String.format("w%d", random.nextInt(12)),
                    new TermCount(random.nextInt(4))
                );
                corpus.replace(doc, terms);
            }
        }
        final int base = VidrioPostListTest.DOCS * rev;
        for (int idx = 1; idx <= 500; ++idx) {
            corpus.addRandom(DocId.of(base + idx), VidrioPostListTest.PER_DOC);
        }
    }

    private static List<String> check(
        final Corpus corpus, final VidrioTable table
    ) {
        final List<String> faults = new ArrayList<>(0);
        final List<String> terms = new ArrayList<>(13);
        terms.add(Corpus.EVERY);
        for (int idx = 0; idx < 12; ++idx) {
            terms.add(String.format("w%d", idx));
        }
        for (final String term : terms) {
            if (!corpus.expected(term).equals(Readback.postings(table, term))) {
                faults.add(term);
            }
        }
        if (!corpus.expectedLengths().equals(Readback.lengths(table))) {
            faults.add("lengths");
        }
        return faults;
    }

    private static VidrioTable fixture() throws URISyntaxException {
        return VidrioTable.open(
            Path.of(
                VidrioPostListTest.class.getResource("oversized/postlist.bin")
                    .toURI()
            ),
            new TableRoot(
                new BlockNumber(0L), 0, BlockSize.MIN, new RevisionNumber(1L),
                false
            )
        );
    }

    private static Class<?> failure(final Runnable action) {
        Class<?> result = Void.class;
        try {
            action.run();
        } catch (final InvalidOperationError ex) {
            result = ex.getClass();
        }
        return result;
    }
}
