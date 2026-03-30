/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.DocLength;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the read side of {@link InMemoryShard}.
 *
 * @since 0.1.0
 */
final class InMemoryDatabaseTest {

    @Test
    void countsDocuments() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(new TermCount(3), db.docCount(), "Count");
        }
    }

    @Test
    void reportsLastDocId() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(DocId.of(3), db.lastDocId(), "Last id");
        }
    }

    @Test
    void emptyDatabaseHasNoLastDocId() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertEquals(DocId.NONE, db.lastDocId(), "No id");
        }
    }

    @Test
    void sumsTotalLengthOfWdfs() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TotalLength(8L), db.totalLength(), "Total length"
            );
        }
    }

    @Test
    void averagesLength() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new DocLength(8.0 / 3.0), db.averageLength(), "Average"
            );
        }
    }

    @Test
    void emptyDatabaseHasZeroAverage() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertEquals(
                DocLength.ZERO, db.averageLength(), "Zero average"
            );
        }
    }

    @Test
    void countsTermFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.termFrequency("apple"), "Frequency"
            );
        }
    }

    @Test
    void unknownTermHasZeroFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.termFrequency("zebra"), "Unknown"
            );
        }
    }

    @Test
    void emptyTermFrequencyIsDocCount() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.termFrequency(""), "Every document"
            );
        }
    }

    @Test
    void sumsCollectionFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(4), db.collectionFrequency("banana"), "Sum"
            );
        }
    }

    @Test
    void unknownTermHasZeroCollectionFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.collectionFrequency("zebra"), "Unknown"
            );
        }
    }

    @Test
    void emptyTermCollectionFrequencyIsDocCount() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.collectionFrequency(""), "Every doc"
            );
        }
    }

    @Test
    void knownTermExists() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.termExists("cherry"), "Exists");
        }
    }

    @Test
    void unknownTermDoesNotExist() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertFalse(db.termExists("zebra"), "Absent");
        }
    }

    @Test
    void emptyTermExistsWithDocuments() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.termExists(""), "Documents exist");
        }
    }

    @Test
    void emptyTermAbsentWithoutDocuments() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertFalse(db.termExists(""), "No documents");
        }
    }

    @Test
    void docLengthIsSumOfWdfs() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(5), db.docLength(DocId.of(1)), "Sum of wdfs"
            );
        }
    }

    @Test
    void docLengthOfMissingDocFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.docLength(DocId.of(9)),
                "Missing document"
            );
        }
    }

    @Test
    void docLengthOfZeroIdFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.docLength(DocId.NONE),
                "Zero id"
            );
        }
    }

    @Test
    void countsUniqueTerms() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.uniqueTerms(DocId.of(1)), "Distinct"
            );
        }
    }

    @Test
    void uniqueTermsCappedByLength() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addTerm("a", TermCount.ZERO);
            doc.addTerm("b", new TermCount(1));
            Assertions.assertEquals(
                new TermCount(1),
                db.uniqueTerms(db.addDocument(doc)),
                "Capped by length"
            );
        }
    }

    @Test
    void findsHighestWdf() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.wdfDocMax(DocId.of(1)), "Highest wdf"
            );
        }
    }

    @Test
    void highestWdfOfEmptyDocIsZero() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertEquals(
                TermCount.ZERO,
                db.wdfDocMax(db.addDocument(new DocumentInternal())),
                "Zero"
            );
        }
    }

    @Test
    void lengthLowerBoundIsOne() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(1), db.docLengthLowerBound(), "Lower bound"
            );
        }
    }

    @Test
    void lengthUpperBoundIsTotal() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(8), db.docLengthUpperBound(), "Upper bound"
            );
        }
    }

    @Test
    void wdfUpperBoundIsCollectionFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.wdfUpperBound("apple"), "Wdf bound"
            );
        }
    }

    @Test
    void emptyTermWdfUpperBoundIsZero() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.wdfUpperBound(""), "Empty term"
            );
        }
    }

    @Test
    void countsValueFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.valueFrequency(new ValueNo(1L)), "Slot"
            );
        }
    }

    @Test
    void tracksValueLowerBound() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "a", db.valueLowerBound(new ValueNo(1L)), "Lower bound"
            );
        }
    }

    @Test
    void tracksValueUpperBound() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "b", db.valueUpperBound(new ValueNo(1L)), "Upper bound"
            );
        }
    }

    @Test
    void unusedSlotHasNoFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.valueFrequency(new ValueNo(5L)), "Unused"
            );
        }
    }

    @Test
    void unusedSlotHasEmptyBound() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "", db.valueUpperBound(new ValueNo(5L)), "Unused"
            );
        }
    }

    @Test
    void reportsPositions() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.hasPositions(), "Positions present");
        }
    }

    @Test
    void reportsNoPositions() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.addDocument(Corpus.third());
            Assertions.assertFalse(db.hasPositions(), "No positions");
        }
    }

    @Test
    void listsPostingDocuments() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            Assertions.assertEquals(
                Corpus.ids(1, 2), Corpus.docs(iter), "Postings"
            );
        }
    }

    @Test
    void listsPostingWdfs() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("banana")
        ) {
            Assertions.assertEquals(
                List.of(new TermCount(3), new TermCount(1)),
                Corpus.wdfs(iter),
                "Wdfs"
            );
        }
    }

    @Test
    void postingReportsDocLength() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            iter.next();
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.docLength(), "Length of document 2"
            );
        }
    }

    @Test
    void postingReportsUniqueTerms() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("banana")
        ) {
            iter.next();
            iter.next();
            Assertions.assertEquals(
                new TermCount(1), iter.uniqueTerms(), "Terms of document 3"
            );
        }
    }

    @Test
    void postingReportsPositionCount() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.positionCount(), "Two positions"
            );
        }
    }

    @Test
    void postingReportsPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            iter.next();
            try (PositionIterator positions = iter.positions()) {
                Assertions.assertEquals(
                    Corpus.at(1, 5), Corpus.positions(positions), "Positions"
                );
            }
        }
    }

    @Test
    void postingSkipsForward() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("banana")
        ) {
            iter.skipTo(DocId.of(2));
            Assertions.assertEquals(DocId.of(3), iter.next(), "Skipped");
        }
    }

    @Test
    void postingNeverSkipsBackwards() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            iter.next();
            iter.next();
            iter.skipTo(DocId.of(1));
            Assertions.assertFalse(iter.hasNext(), "Stays at the end");
        }
    }

    @Test
    void unknownTermHasNoPostings() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("zebra")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No postings");
        }
    }

    @Test
    void postingsSeeLaterDeletion() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("apple")
        ) {
            db.deleteDocument(DocId.of(2));
            Assertions.assertEquals(
                Corpus.ids(1), Corpus.docs(iter), "Deleted posting skipped"
            );
        }
    }

    @Test
    void emptyTermListsEveryDocument() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            try (PostingIterator iter = db.postings("")) {
                Assertions.assertEquals(
                    Corpus.ids(1, 3), Corpus.docs(iter), "Every document"
                );
            }
        }
    }

    @Test
    void everyDocumentHasWdfOne() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            Assertions.assertEquals(
                List.of(new TermCount(1), new TermCount(1), new TermCount(1)),
                Corpus.wdfs(iter),
                "Wdf one"
            );
        }
    }

    @Test
    void everyDocumentReportsLength() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(5), iter.docLength(), "Length of document 1"
            );
        }
    }

    @Test
    void everyDocumentReportsUniqueTerms() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.uniqueTerms(), "Terms of document 1"
            );
        }
    }

    @Test
    void everyDocumentSkipsForward() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.skipTo(DocId.of(3));
            Assertions.assertEquals(DocId.of(3), iter.next(), "Skipped");
        }
    }

    @Test
    void everyDocumentHasNoPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.next();
            Assertions.assertThrows(
                UnimplementedError.class, iter::positions, "No positions"
            );
        }
    }

    @Test
    void everyDocumentHasNoPositionCount() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.next();
            Assertions.assertThrows(
                UnimplementedError.class, iter::positionCount, "No count"
            );
        }
    }

    @Test
    void closedPostingsEnd() {
        try (WritableDatabase db = Corpus.build()) {
            final PostingIterator iter = db.postings("");
            iter.close();
            Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
        }
    }

    @Test
    void listsDocumentTerms() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(2))
        ) {
            Assertions.assertEquals(
                List.of("apple", "cherry"), Corpus.terms(iter), "Terms"
            );
        }
    }

    @Test
    void documentTermReportsWdf() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.skipTo("b");
            iter.next();
            Assertions.assertEquals(new TermCount(3), iter.wdf(), "Wdf");
        }
    }

    @Test
    void documentTermReportsTermFrequency() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(2))
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.termFrequency(), "Term frequency"
            );
        }
    }

    @Test
    void documentTermReportsPositions() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(2))
        ) {
            iter.next();
            iter.next();
            try (PositionIterator positions = iter.positions()) {
                Assertions.assertEquals(
                    Corpus.at(3), Corpus.positions(positions), "Positions"
                );
            }
        }
    }

    @Test
    void documentTermReportsPositionCount() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.positionCount(), "Position count"
            );
        }
    }

    @Test
    void termListOfMissingDocFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.termList(DocId.of(4)),
                "Missing document"
            );
        }
    }

    @Test
    void readsPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "apple")
        ) {
            Assertions.assertEquals(
                Corpus.at(1, 5), Corpus.positions(iter), "Positions"
            );
        }
    }

    @Test
    void unpositionedTermHasNoPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "banana")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No positions");
        }
    }

    @Test
    void absentTermHasNoPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "cherry")
        ) {
            Assertions.assertFalse(iter.hasNext(), "Term not in document");
        }
    }

    @Test
    void missingDocumentHasNoPositions() {
        try (
            WritableDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(7), "apple")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No document");
        }
    }

    @Test
    void positionsRejectZeroId() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.positions(DocId.NONE, "apple"),
                "Zero id"
            );
        }
    }

    @Test
    void positionsRejectEmptyTerm() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.positions(DocId.of(1), ""),
                "Empty term"
            );
        }
    }

    @Test
    void countsPositions() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2),
                db.positionCount(DocId.of(1), "apple"),
                "Position count"
            );
        }
    }

    @Test
    void absentPairHasNoPositionCount() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO,
                db.positionCount(DocId.of(3), "apple"),
                "No positions"
            );
        }
    }

    @Test
    void readsDocumentData() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "two", db.document(DocId.of(2)).data(), "Data"
            );
        }
    }

    @Test
    void readsDocumentValue() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                Optional.of("x"),
                db.document(DocId.of(1)).value(new ValueNo(2L)),
                "Value"
            );
        }
    }

    @Test
    void readsDocumentPositions() {
        try (WritableDatabase db = Corpus.build()) {
            final Document doc = db.document(DocId.of(1));
            try (
                TermIterator iter = doc.termList();
                PositionIterator positions = InMemoryDatabaseTest.open(iter)
            ) {
                Assertions.assertEquals(
                    Corpus.at(1, 5), Corpus.positions(positions), "Positions"
                );
            }
        }
    }

    @Test
    void documentKeepsWdfAbovePositionCount() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addPosting("a", new TermPos(1), new TermCount(4));
            try (TermIterator iter = db.document(db.addDocument(doc)).termList()) {
                iter.next();
                Assertions.assertEquals(new TermCount(4), iter.wdf(), "Wdf");
            }
        }
    }

    @Test
    void documentIsDetached() {
        try (WritableDatabase db = Corpus.build()) {
            db.document(DocId.of(3)).addTerm("extra", new TermCount(1));
            Assertions.assertEquals(
                new TermCount(1), db.docLength(DocId.of(3)), "Unchanged"
            );
        }
    }

    @Test
    void missingDocumentFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.document(DocId.of(4)),
                "Missing document"
            );
        }
    }

    @Test
    void zeroDocumentIdFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.document(DocId.NONE),
                "Zero id"
            );
        }
    }

    @Test
    void cannotCombineDatabases() {
        try (
            WritableDatabase db = Corpus.build();
            WritableDatabase other = DatabaseFactory.inMemory()
        ) {
            Assertions.assertThrows(
                UnimplementedError.class,
                () -> db.addDatabase(other),
                "No combination"
            );
        }
    }

    private static PositionIterator open(final TermIterator iter) {
        iter.next();
        return iter.positions();
    }
}
