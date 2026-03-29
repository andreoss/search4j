/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
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
 * Tests of the read side of {@link InMemoryDatabase}.
 *
 * @since 0.1.0
 */
final class InMemoryDatabaseTest {

    @Test
    void countsDocuments() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(new TermCount(3), db.docCount(), "Count");
        }
    }

    @Test
    void reportsLastDocId() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(DocId.of(3), db.lastDocId(), "Last id");
        }
    }

    @Test
    void emptyDatabaseHasNoLastDocId() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            Assertions.assertEquals(DocId.NONE, db.lastDocId(), "No id");
        }
    }

    @Test
    void sumsTotalLengthOfWdfs() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TotalLength(8L), db.totalLength(), "Total length"
            );
        }
    }

    @Test
    void averagesLength() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new DocLength(8.0 / 3.0), db.averageLength(), "Average"
            );
        }
    }

    @Test
    void emptyDatabaseHasZeroAverage() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            Assertions.assertEquals(
                DocLength.ZERO, db.averageLength(), "Zero average"
            );
        }
    }

    @Test
    void countsTermFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.termFrequency("apple"), "Frequency"
            );
        }
    }

    @Test
    void unknownTermHasZeroFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.termFrequency("zebra"), "Unknown"
            );
        }
    }

    @Test
    void emptyTermFrequencyIsDocCount() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.termFrequency(""), "Every document"
            );
        }
    }

    @Test
    void sumsCollectionFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(4), db.collectionFrequency("banana"), "Sum"
            );
        }
    }

    @Test
    void unknownTermHasZeroCollectionFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.collectionFrequency("zebra"), "Unknown"
            );
        }
    }

    @Test
    void emptyTermCollectionFrequencyIsDocCount() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.collectionFrequency(""), "Every doc"
            );
        }
    }

    @Test
    void knownTermExists() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.termExists("cherry"), "Exists");
        }
    }

    @Test
    void unknownTermDoesNotExist() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertFalse(db.termExists("zebra"), "Absent");
        }
    }

    @Test
    void emptyTermExistsWithDocuments() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.termExists(""), "Documents exist");
        }
    }

    @Test
    void emptyTermAbsentWithoutDocuments() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            Assertions.assertFalse(db.termExists(""), "No documents");
        }
    }

    @Test
    void docLengthIsSumOfWdfs() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(5), db.docLength(DocId.of(1)), "Sum of wdfs"
            );
        }
    }

    @Test
    void docLengthOfMissingDocFails() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.docLength(DocId.of(9)),
                "Missing document"
            );
        }
    }

    @Test
    void docLengthOfZeroIdFails() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.docLength(DocId.NONE),
                "Zero id"
            );
        }
    }

    @Test
    void countsUniqueTerms() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.uniqueTerms(DocId.of(1)), "Distinct"
            );
        }
    }

    @Test
    void uniqueTermsCappedByLength() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            final InMemoryDocument doc = new InMemoryDocument();
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
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.wdfDocMax(DocId.of(1)), "Highest wdf"
            );
        }
    }

    @Test
    void highestWdfOfEmptyDocIsZero() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            Assertions.assertEquals(
                TermCount.ZERO,
                db.wdfDocMax(db.addDocument(new InMemoryDocument())),
                "Zero"
            );
        }
    }

    @Test
    void lengthLowerBoundIsOne() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(1), db.docLengthLowerBound(), "Lower bound"
            );
        }
    }

    @Test
    void lengthUpperBoundIsTotal() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(8), db.docLengthUpperBound(), "Upper bound"
            );
        }
    }

    @Test
    void wdfUpperBoundIsCollectionFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(3), db.wdfUpperBound("apple"), "Wdf bound"
            );
        }
    }

    @Test
    void emptyTermWdfUpperBoundIsZero() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.wdfUpperBound(""), "Empty term"
            );
        }
    }

    @Test
    void countsValueFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2), db.valueFrequency(new ValueNo(1L)), "Slot"
            );
        }
    }

    @Test
    void tracksValueLowerBound() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "a", db.valueLowerBound(new ValueNo(1L)), "Lower bound"
            );
        }
    }

    @Test
    void tracksValueUpperBound() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "b", db.valueUpperBound(new ValueNo(1L)), "Upper bound"
            );
        }
    }

    @Test
    void unusedSlotHasNoFrequency() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO, db.valueFrequency(new ValueNo(5L)), "Unused"
            );
        }
    }

    @Test
    void unusedSlotHasEmptyBound() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "", db.valueUpperBound(new ValueNo(5L)), "Unused"
            );
        }
    }

    @Test
    void reportsPositions() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertTrue(db.hasPositions(), "Positions present");
        }
    }

    @Test
    void reportsNoPositions() {
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            db.addDocument(Corpus.third());
            Assertions.assertFalse(db.hasPositions(), "No positions");
        }
    }

    @Test
    void listsPostingDocuments() {
        try (
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("banana")
        ) {
            iter.skipTo(DocId.of(2));
            Assertions.assertEquals(DocId.of(3), iter.next(), "Skipped");
        }
    }

    @Test
    void postingNeverSkipsBackwards() {
        try (
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("zebra")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No postings");
        }
    }

    @Test
    void postingsSeeLaterDeletion() {
        try (
            InMemoryDatabase db = Corpus.build();
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
        try (InMemoryDatabase db = Corpus.build()) {
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            iter.skipTo(DocId.of(3));
            Assertions.assertEquals(DocId.of(3), iter.next(), "Skipped");
        }
    }

    @Test
    void everyDocumentHasNoPositions() {
        try (
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
        try (InMemoryDatabase db = Corpus.build()) {
            final PostingIterator iter = db.postings("");
            iter.close();
            Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
        }
    }

    @Test
    void listsDocumentTerms() {
        try (
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
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
        try (InMemoryDatabase db = Corpus.build()) {
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
            InMemoryDatabase db = Corpus.build();
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
            InMemoryDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "banana")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No positions");
        }
    }

    @Test
    void absentTermHasNoPositions() {
        try (
            InMemoryDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(1), "cherry")
        ) {
            Assertions.assertFalse(iter.hasNext(), "Term not in document");
        }
    }

    @Test
    void missingDocumentHasNoPositions() {
        try (
            InMemoryDatabase db = Corpus.build();
            PositionIterator iter = db.positions(DocId.of(7), "apple")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No document");
        }
    }

    @Test
    void positionsRejectZeroId() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.positions(DocId.NONE, "apple"),
                "Zero id"
            );
        }
    }

    @Test
    void positionsRejectEmptyTerm() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.positions(DocId.of(1), ""),
                "Empty term"
            );
        }
    }

    @Test
    void countsPositions() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                new TermCount(2),
                db.positionCount(DocId.of(1), "apple"),
                "Position count"
            );
        }
    }

    @Test
    void absentPairHasNoPositionCount() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                TermCount.ZERO,
                db.positionCount(DocId.of(3), "apple"),
                "No positions"
            );
        }
    }

    @Test
    void readsDocumentData() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                "two", db.document(DocId.of(2)).data(), "Data"
            );
        }
    }

    @Test
    void readsDocumentValue() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                Optional.of("x"),
                db.document(DocId.of(1)).value(new ValueNo(2L)),
                "Value"
            );
        }
    }

    @Test
    void readsDocumentPositions() {
        try (InMemoryDatabase db = Corpus.build()) {
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
        try (InMemoryDatabase db = new InMemoryDatabase()) {
            final InMemoryDocument doc = new InMemoryDocument();
            doc.addPosting("a", new TermPos(1), new TermCount(4));
            try (TermIterator iter = db.document(db.addDocument(doc)).termList()) {
                iter.next();
                Assertions.assertEquals(new TermCount(4), iter.wdf(), "Wdf");
            }
        }
    }

    @Test
    void documentIsDetached() {
        try (InMemoryDatabase db = Corpus.build()) {
            db.document(DocId.of(3)).addTerm("extra", new TermCount(1));
            Assertions.assertEquals(
                new TermCount(1), db.docLength(DocId.of(3)), "Unchanged"
            );
        }
    }

    @Test
    void missingDocumentFails() {
        try (InMemoryDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.document(DocId.of(4)),
                "Missing document"
            );
        }
    }

    @Test
    void zeroDocumentIdFails() {
        try (InMemoryDatabase db = Corpus.build()) {
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
            InMemoryDatabase db = Corpus.build();
            InMemoryDatabase other = new InMemoryDatabase()
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
