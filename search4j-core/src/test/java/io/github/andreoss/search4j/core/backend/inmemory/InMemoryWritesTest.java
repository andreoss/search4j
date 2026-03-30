/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the write side of {@link InMemoryShard}.
 *
 * @since 0.1.0
 */
final class InMemoryWritesTest {

    @Test
    void addAssignsSequentialIds() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                DocId.of(4), db.addDocument(Corpus.plain("d")), "Next id"
            );
        }
    }

    @Test
    void addCopiesValues() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            final DocumentInternal doc = Corpus.first();
            final DocId docid = db.addDocument(doc);
            doc.addValue(new ValueNo(1L), "changed");
            Assertions.assertEquals(
                "b", db.document(docid).value(new ValueNo(1L)).orElse(""),
                "Value copied"
            );
        }
    }

    @Test
    void addCopiesPositions() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            final DocumentInternal doc = Corpus.second();
            final DocId docid = db.addDocument(doc);
            doc.addPosting("apple", new TermPos(9), new TermCount(1));
            try (PositionIterator iter = db.positions(docid, "apple")) {
                Assertions.assertEquals(
                    Corpus.at(2), Corpus.positions(iter), "Positions copied"
                );
            }
        }
    }

    @Test
    void deleteReducesCount() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(new TermCount(2), db.docCount(), "Count");
        }
    }

    @Test
    void deleteReducesTermFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(
                new TermCount(1), db.termFrequency("apple"), "Frequency"
            );
        }
    }

    @Test
    void deleteReducesCollectionFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(
                new TermCount(1), db.collectionFrequency("banana"), "Sum"
            );
        }
    }

    @Test
    void deleteReducesTotalLength() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(
                new TotalLength(3L), db.totalLength(), "Total length"
            );
        }
    }

    @Test
    void deleteDropsUnusedTerm() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            Assertions.assertFalse(db.termExists("cherry"), "Term gone");
        }
    }

    @Test
    void deleteKeepsLastDocId() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(3));
            Assertions.assertEquals(DocId.of(3), db.lastDocId(), "Last id");
        }
    }

    @Test
    void deletedIdIsNotReused() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(3));
            Assertions.assertEquals(
                DocId.of(4), db.addDocument(Corpus.plain("d")), "Fresh id"
            );
        }
    }

    @Test
    void deleteReducesValueFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            Assertions.assertEquals(
                new TermCount(1), db.valueFrequency(new ValueNo(1L)), "Slot"
            );
        }
    }

    @Test
    void deleteKeepsLooseValueBound() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            Assertions.assertEquals(
                "a", db.valueLowerBound(new ValueNo(1L)), "Bound not tightened"
            );
        }
    }

    @Test
    void deleteResetsBoundsOfUnusedSlot() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(
                "", db.valueUpperBound(new ValueNo(2L)), "Bound reset"
            );
        }
    }

    @Test
    void deleteResetsLowerBoundOfUnusedSlot() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            Assertions.assertEquals(
                "", db.valueLowerBound(new ValueNo(2L)), "Bound reset"
            );
        }
    }

    @Test
    void deletingEveryDocumentClearsPositions() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.deleteDocument(db.addDocument(Corpus.second()));
            Assertions.assertFalse(db.hasPositions(), "No positions");
        }
    }

    @Test
    void deletingSomeDocumentsKeepsPositions() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            db.deleteDocument(DocId.of(2));
            Assertions.assertTrue(db.hasPositions(), "Crude flag kept");
        }
    }

    @Test
    void deleteMissingDocumentFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.deleteDocument(DocId.of(4)),
                "Missing document"
            );
        }
    }

    @Test
    void deleteZeroIdFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.deleteDocument(DocId.NONE),
                "Zero id"
            );
        }
    }

    @Test
    void deleteTwiceFails() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> db.deleteDocument(DocId.of(2)),
                "Already deleted"
            );
        }
    }

    @Test
    void deleteByTermRemovesEveryIndexedDocument() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument("apple");
            Assertions.assertEquals(new TermCount(1), db.docCount(), "Count");
        }
    }

    @Test
    void deleteByUnknownTermDoesNothing() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument("zebra");
            Assertions.assertEquals(new TermCount(3), db.docCount(), "Count");
        }
    }

    @Test
    void deleteByEmptyTermFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.deleteDocument(""),
                "Empty term"
            );
        }
    }

    @Test
    void replaceSwapsData() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(1), Corpus.plain("new"));
            Assertions.assertEquals(
                "new", db.document(DocId.of(1)).data(), "Data replaced"
            );
        }
    }

    @Test
    void replaceDropsOldTerms() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(1), Corpus.plain("new"));
            Assertions.assertEquals(
                new TermCount(1), db.termFrequency("banana"), "Old term gone"
            );
        }
    }

    @Test
    void replaceAdjustsTotalLength() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(1), Corpus.plain("new"));
            Assertions.assertEquals(
                new TotalLength(4L), db.totalLength(), "Total length"
            );
        }
    }

    @Test
    void replaceAdjustsValueFrequency() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(1), Corpus.plain("new"));
            Assertions.assertEquals(
                TermCount.ZERO, db.valueFrequency(new ValueNo(2L)), "Slot"
            );
        }
    }

    @Test
    void replaceKeepsCount() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(2), Corpus.plain("new"));
            Assertions.assertEquals(new TermCount(3), db.docCount(), "Count");
        }
    }

    @Test
    void replaceBeyondLastAdds() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(7), Corpus.plain("new"));
            Assertions.assertEquals(DocId.of(7), db.lastDocId(), "Last id");
        }
    }

    @Test
    void replaceBeyondLastCounts() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(7), Corpus.plain("new"));
            Assertions.assertEquals(new TermCount(4), db.docCount(), "Count");
        }
    }

    @Test
    void addFollowsReplacedId() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument(DocId.of(7), Corpus.plain("new"));
            Assertions.assertEquals(
                DocId.of(8), db.addDocument(Corpus.plain("d")), "Next id"
            );
        }
    }

    @Test
    void replaceBelowLastKeepsLastId() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            db.replaceDocument(DocId.of(2), Corpus.plain("back"));
            Assertions.assertEquals(DocId.of(3), db.lastDocId(), "Last id");
        }
    }

    @Test
    void replaceRestoresDeletedDocument() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            db.replaceDocument(DocId.of(2), Corpus.plain("back"));
            Assertions.assertEquals(
                "back", db.document(DocId.of(2)).data(), "Restored"
            );
        }
    }

    @Test
    void replaceZeroIdFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.replaceDocument(DocId.NONE, Corpus.plain("x")),
                "Zero id"
            );
        }
    }

    @Test
    void replaceByUnknownTermAdds() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                DocId.of(4),
                db.replaceDocument("zebra", Corpus.plain("zebra")),
                "Added"
            );
        }
    }

    @Test
    void replaceByTermReusesFirstId() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertEquals(
                DocId.of(1),
                db.replaceDocument("banana", Corpus.plain("kiwi")),
                "First id"
            );
        }
    }

    @Test
    void replaceByTermDeletesOthers() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument("banana", Corpus.plain("kiwi"));
            try (PostingIterator iter = db.postings("")) {
                Assertions.assertEquals(
                    Corpus.ids(1, 2), Corpus.docs(iter), "Others deleted"
                );
            }
        }
    }

    @Test
    void replaceByTermStoresDocument() {
        try (WritableDatabase db = Corpus.build()) {
            db.replaceDocument("banana", Corpus.plain("kiwi"));
            Assertions.assertEquals(
                "kiwi", db.document(DocId.of(1)).data(), "Stored"
            );
        }
    }

    @Test
    void replaceByEmptyTermFails() {
        try (WritableDatabase db = Corpus.build()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.replaceDocument("", Corpus.plain("x")),
                "Empty term"
            );
        }
    }

    @Test
    void cancelKeepsChanges() {
        try (WritableDatabase db = Corpus.build()) {
            db.addDocument(Corpus.plain("d"));
            db.cancel();
            Assertions.assertEquals(new TermCount(4), db.docCount(), "Kept");
        }
    }

    @Test
    void commitKeepsChanges() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(1));
            db.commit();
            Assertions.assertEquals(new TermCount(2), db.docCount(), "Kept");
        }
    }

    @Test
    void storesMetadata() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.setMetadata("key", "value");
            Assertions.assertEquals("value", db.metadata("key"), "Metadata");
        }
    }

    @Test
    void unsetMetadataIsEmpty() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertEquals("", db.metadata("key"), "Unset");
        }
    }

    @Test
    void emptyMetadataRemovesKey() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.setMetadata("key", "value");
            db.setMetadata("key", "");
            Assertions.assertEquals("", db.metadata("key"), "Removed");
        }
    }

    @Test
    void readingEmptyMetadataKeyFails() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> db.metadata(""), "Empty key"
            );
        }
    }

    @Test
    void writingEmptyMetadataKeyFails() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> db.setMetadata("", "value"),
                "Empty key"
            );
        }
    }

    @Test
    void zeroWdfTermStillCountsDocument() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addTerm("a", TermCount.ZERO);
            db.addDocument(doc);
            Assertions.assertEquals(
                List.of(new TermCount(1), TermCount.ZERO),
                List.of(db.termFrequency("a"), db.collectionFrequency("a")),
                "Frequencies"
            );
        }
    }
}
