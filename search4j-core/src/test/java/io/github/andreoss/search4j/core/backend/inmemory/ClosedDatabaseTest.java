/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of a closed {@link InMemoryShard}.
 *
 * @since 0.1.0
 */
final class ClosedDatabaseTest {

    @Test
    void docCountFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().docCount(),
            "Count"
        );
    }

    @Test
    void lastDocIdFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().lastDocId(),
            "Last id"
        );
    }

    @Test
    void averageLengthFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().averageLength(),
            "Average"
        );
    }

    @Test
    void termFrequencyFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().termFrequency("apple"),
            "Frequency"
        );
    }

    @Test
    void collectionFrequencyFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().collectionFrequency("apple"),
            "Collection frequency"
        );
    }

    @Test
    void termExistsFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().termExists("apple"),
            "Exists"
        );
    }

    @Test
    void docLengthFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().docLength(DocId.of(1)),
            "Length"
        );
    }

    @Test
    void uniqueTermsFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().uniqueTerms(DocId.of(1)),
            "Unique terms"
        );
    }

    @Test
    void wdfUpperBoundFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().wdfUpperBound("apple"),
            "Wdf bound"
        );
    }

    @Test
    void valueFrequencyFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().valueFrequency(new ValueNo(1L)),
            "Value frequency"
        );
    }

    @Test
    void valueLowerBoundFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().valueLowerBound(new ValueNo(1L)),
            "Lower bound"
        );
    }

    @Test
    void valueUpperBoundFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().valueUpperBound(new ValueNo(1L)),
            "Upper bound"
        );
    }

    @Test
    void hasPositionsFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().hasPositions(),
            "Positions flag"
        );
    }

    @Test
    void postingsFail() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().postings("apple"),
            "Postings"
        );
    }

    @Test
    void termListFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().termList(DocId.of(1)),
            "Term list"
        );
    }

    @Test
    void positionsFail() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().positions(DocId.of(1), "apple"),
            "Positions"
        );
    }

    @Test
    void allTermsFail() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().allTerms(""),
            "All terms"
        );
    }

    @Test
    void valueStreamFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().valueStream(new ValueNo(1L)),
            "Value stream"
        );
    }

    @Test
    void metadataFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().metadata("key"),
            "Metadata"
        );
    }

    @Test
    void metadataKeysFail() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().metadataKeys(""),
            "Metadata keys"
        );
    }

    @Test
    void documentFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().document(DocId.of(1)),
            "Document"
        );
    }

    @Test
    void addFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().addDocument(Corpus.plain("a")),
            "Add"
        );
    }

    @Test
    void deleteFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().deleteDocument(DocId.of(1)),
            "Delete"
        );
    }

    @Test
    void deleteByTermFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().deleteDocument("apple"),
            "Delete by term"
        );
    }

    @Test
    void replaceFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().replaceDocument(
                DocId.of(2), Corpus.plain("a")
            ),
            "Replace"
        );
    }

    @Test
    void replaceByTermFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().replaceDocument(
                "apple", Corpus.plain("a")
            ),
            "Replace by term"
        );
    }

    @Test
    void setMetadataFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().setMetadata("key", "value"),
            "Set metadata"
        );
    }

    @Test
    void commitFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().commit(),
            "Commit"
        );
    }

    @Test
    void cancelFails() {
        Assertions.assertThrows(
            DatabaseClosedError.class,
            () -> ClosedDatabaseTest.closed().cancel(),
            "Cancel"
        );
    }

    @Test
    void totalLengthStaysReadable() {
        Assertions.assertEquals(
            new TotalLength(8L),
            ClosedDatabaseTest.closed().totalLength(),
            "Total length"
        );
    }

    @Test
    void lengthUpperBoundStaysReadable() {
        Assertions.assertEquals(
            new TermCount(8),
            ClosedDatabaseTest.closed().docLengthUpperBound(),
            "Upper bound"
        );
    }

    @Test
    void lengthLowerBoundStaysReadable() {
        Assertions.assertEquals(
            new TermCount(1),
            ClosedDatabaseTest.closed().docLengthLowerBound(),
            "Lower bound"
        );
    }

    @Test
    void emptyTermWdfBoundStaysReadable() {
        Assertions.assertEquals(
            TermCount.ZERO,
            ClosedDatabaseTest.closed().wdfUpperBound(""),
            "Empty term bound"
        );
    }

    @Test
    void closingTwiceIsAllowed() {
        final WritableDatabase database = ClosedDatabaseTest.closed();
        database.close();
        Assertions.assertEquals(
            new TotalLength(8L), database.totalLength(), "Still closed"
        );
    }

    @Test
    void openPostingsFailAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::hasNext, "Postings"
            );
        }
    }

    @Test
    void openPostingAccessorsFailAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            iter.next();
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::wdf, "Posting wdf"
            );
        }
    }

    @Test
    void openPostingAdvanceFailsAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::next, "Posting advance"
            );
        }
    }

    @Test
    void openEveryDocumentFailsAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::hasNext, "Every document"
            );
        }
    }

    @Test
    void openTermListFailsAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::next, "Term list"
            );
        }
    }

    @Test
    void openTermFrequencyFailsAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::termFrequency, "Frequency"
            );
        }
    }

    @Test
    void openAllTermsFailAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::hasNext, "All terms"
            );
        }
    }

    @Test
    void openMetadataKeysFailAfterClose() {
        try (WritableDatabase db = Corpus.build()) {
            db.setMetadata("k", "v");
            try (TermIterator iter = db.metadataKeys("")) {
                ClosedDatabaseTest.shut(db);
                Assertions.assertThrows(
                    DatabaseClosedError.class, iter::hasNext, "Keys"
                );
            }
        }
    }

    @Test
    void openStreamFailsAfterClose() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(1L))
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertThrows(
                DatabaseClosedError.class, iter::hasNext, "Stream"
            );
        }
    }

    @Test
    void openPositionsSurviveClose() {
        try (
            WritableDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "apple")
        ) {
            ClosedDatabaseTest.shut(db);
            Assertions.assertEquals(
                Corpus.at(1, 5), Corpus.positions(iter), "Copied positions"
            );
        }
    }

    private static WritableDatabase closed() {
        final WritableDatabase database = Corpus.build();
        database.close();
        return database;
    }

    private static void shut(final WritableDatabase database) {
        database.close();
    }
}
