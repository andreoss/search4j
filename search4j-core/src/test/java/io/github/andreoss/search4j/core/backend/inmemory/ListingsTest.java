/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the term, metadata and value listings of {@link InMemoryShard}.
 *
 * @since 0.1.0
 */
final class ListingsTest {

    @Test
    void listsAllTerms() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            Assertions.assertEquals(
                List.of("apple", "banana", "cherry"), Corpus.terms(iter), "All"
            );
        }
    }

    @Test
    void listsPrefixedTerms() {
        try (WritableDatabase db = Corpus.build()) {
            db.addDocument(Corpus.plain("bb"));
            db.addDocument(Corpus.plain("c"));
            try (TermIterator iter = db.allTerms("b")) {
                Assertions.assertEquals(
                    List.of("banana", "bb"), Corpus.terms(iter), "Prefixed"
                );
            }
        }
    }

    @Test
    void unmatchedPrefixListsNothing() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("z")
        ) {
            Assertions.assertFalse(iter.hasNext(), "No terms");
        }
    }

    @Test
    void allTermsReportFrequency() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("ban")
        ) {
            iter.next();
            Assertions.assertEquals(
                new TermCount(2), iter.termFrequency(), "Frequency"
            );
        }
    }

    @Test
    void allTermsSkipForward() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            iter.skipTo("b");
            Assertions.assertEquals("banana", iter.next(), "Skipped");
        }
    }

    @Test
    void allTermsSkipStaysWithinPrefix() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("c")
        ) {
            iter.skipTo("a");
            Assertions.assertEquals("cherry", iter.next(), "Kept to prefix");
        }
    }

    @Test
    void allTermsSkipPastEnd() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            iter.skipTo("d");
            Assertions.assertFalse(iter.hasNext(), "Past the end");
        }
    }

    @Test
    void allTermsEndRaises() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("c")
        ) {
            iter.next();
            Assertions.assertThrows(
                NoSuchElementException.class, iter::next, "At the end"
            );
        }
    }

    @Test
    void allTermsSkipDeletedTerms() {
        try (WritableDatabase db = Corpus.build()) {
            db.deleteDocument(DocId.of(2));
            try (TermIterator iter = db.allTerms("")) {
                Assertions.assertEquals(
                    List.of("apple", "banana"), Corpus.terms(iter), "Live"
                );
            }
        }
    }

    @Test
    void allTermsHaveNoWdf() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            iter.next();
            Assertions.assertThrows(
                InvalidOperationError.class, iter::wdf, "No wdf"
            );
        }
    }

    @Test
    void allTermsHaveNoPositions() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            iter.next();
            Assertions.assertThrows(
                InvalidOperationError.class, iter::positions, "No positions"
            );
        }
    }

    @Test
    void allTermsHaveNoPositionCount() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.allTerms("")
        ) {
            iter.next();
            Assertions.assertThrows(
                InvalidOperationError.class, iter::positionCount, "No count"
            );
        }
    }

    @Test
    void closedAllTermsEnd() {
        try (WritableDatabase db = Corpus.build()) {
            final TermIterator iter = db.allTerms("");
            iter.close();
            Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
        }
    }

    @Test
    void listsMetadataKeys() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.setMetadata("k2", "b");
            db.setMetadata("j", "c");
            db.setMetadata("k1", "a");
            try (TermIterator iter = db.metadataKeys("k")) {
                Assertions.assertEquals(
                    List.of("k1", "k2"), Corpus.terms(iter), "Keys"
                );
            }
        }
    }

    @Test
    void metadataKeysHaveNoFrequency() {
        try (WritableDatabase db = DatabaseFactory.inMemory()) {
            db.setMetadata("k", "v");
            try (TermIterator iter = db.metadataKeys("")) {
                iter.next();
                Assertions.assertThrows(
                    InvalidOperationError.class,
                    iter::termFrequency,
                    "No frequency"
                );
            }
        }
    }

    @Test
    void streamsSlotValues() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(1L))
        ) {
            Assertions.assertEquals(
                List.of("1:b", "2:a"), Corpus.values(iter), "Stream"
            );
        }
    }

    @Test
    void streamSkipsDocumentsWithoutSlot() {
        try (WritableDatabase db = Corpus.build()) {
            final DocumentInternal doc = Corpus.plain("d");
            doc.addValue(new ValueNo(2L), "y");
            db.addDocument(doc);
            try (ValueIterator iter = db.valueStream(new ValueNo(2L))) {
                Assertions.assertEquals(
                    List.of("1:x", "4:y"), Corpus.values(iter), "Holders only"
                );
            }
        }
    }

    @Test
    void streamReportsSlot() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(2L))
        ) {
            iter.next();
            Assertions.assertEquals(new ValueNo(2L), iter.slot(), "Slot");
        }
    }

    @Test
    void streamSkipsForward() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(1L))
        ) {
            iter.skipTo(DocId.of(2));
            Assertions.assertEquals("a", iter.next(), "Skipped");
        }
    }

    @Test
    void streamOfUnusedSlotIsEmpty() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(9L))
        ) {
            Assertions.assertFalse(iter.hasNext(), "Empty stream");
        }
    }

    @Test
    void closedStreamEnds() {
        try (WritableDatabase db = Corpus.build()) {
            final ValueIterator iter = db.valueStream(new ValueNo(1L));
            iter.close();
            Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
        }
    }

    @Test
    void streamSlotBeforeStartFails() {
        try (
            WritableDatabase db = Corpus.build();
            ValueIterator iter = db.valueStream(new ValueNo(1L))
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class, iter::slot, "Not started"
            );
        }
    }
}
