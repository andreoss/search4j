/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link InMemoryDocument}.
 *
 * @since 0.1.0
 */
final class InMemoryDocumentTest {

    @Test
    void startsWithEmptyData() {
        Assertions.assertEquals(
            "", new InMemoryDocument().data(), "Empty data"
        );
    }

    @Test
    void storesData() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.setData("text");
        Assertions.assertEquals("text", doc.data(), "Data read back");
    }

    @Test
    void storesValue() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        Assertions.assertEquals(
            Optional.of("v"), doc.value(new ValueNo(1L)), "Value read back"
        );
    }

    @Test
    void emptyValueClearsSlot() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(1L), "");
        Assertions.assertEquals(
            Optional.empty(), doc.value(new ValueNo(1L)), "Slot cleared"
        );
    }

    @Test
    void removesValue() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        doc.removeValue(new ValueNo(1L));
        Assertions.assertEquals(
            TermCount.ZERO, doc.valueCount(), "Value removed"
        );
    }

    @Test
    void clearsValues() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(2L), "w");
        doc.clearValues();
        Assertions.assertEquals(
            TermCount.ZERO, doc.valueCount(), "Values cleared"
        );
    }

    @Test
    void countsValues() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(2L), "w");
        Assertions.assertEquals(
            new TermCount(2), doc.valueCount(), "Value count"
        );
    }

    @Test
    void iteratesValuesBySlot() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(7L), "late");
        doc.addValue(new ValueNo(2L), "early");
        final List<String> seen = new ArrayList<>(2);
        try (ValueIterator iter = doc.values()) {
            while (iter.hasNext()) {
                seen.add(String.format("%s=%s", iter.next(), iter.slot()));
            }
        }
        Assertions.assertEquals(
            List.of("early=2", "late=7"), seen, "Values in slot order"
        );
    }

    @Test
    void valueIteratorHasNoDocId() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        try (ValueIterator iter = doc.values()) {
            iter.next();
            Assertions.assertThrows(
                InvalidOperationError.class, iter::docId, "No document id"
            );
        }
    }

    @Test
    void valueIteratorCannotSkip() {
        try (ValueIterator iter = new InMemoryDocument().values()) {
            Assertions.assertThrows(
                InvalidOperationError.class,
                () -> iter.skipTo(DocId.of(1)),
                "No skip by document"
            );
        }
    }

    @Test
    void closedValueIteratorEnds() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addValue(new ValueNo(1L), "v");
        final ValueIterator iter = doc.values();
        iter.close();
        Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
    }

    @Test
    void valueIteratorReadsSnapshot() {
        final InMemoryDocument doc = new InMemoryDocument();
        try (ValueIterator iter = doc.values()) {
            doc.addValue(new ValueNo(1L), "v");
            Assertions.assertFalse(iter.hasNext(), "Snapshot unchanged");
        }
    }

    @Test
    void addTermAccumulatesWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(2));
        doc.addTerm("a", new TermCount(3));
        Assertions.assertEquals(
            new TermCount(5), InMemoryDocumentTest.wdf(doc), "Wdf sum"
        );
    }

    @Test
    void zeroWdfTermIsKept() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", TermCount.ZERO);
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Zero wdf term kept"
        );
    }

    @Test
    void addPostingIncreasesWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(4), new TermCount(2));
        Assertions.assertEquals(
            new TermCount(3), InMemoryDocumentTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void repeatedPositionStillIncreasesWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(2), InMemoryDocumentTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void repeatedPositionKeptOnce() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1),
            InMemoryDocumentTest.count(doc),
            "One position"
        );
    }

    @Test
    void positionsAreSorted() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(9), new TermCount(1));
        doc.addPosting("a", new TermPos(3), new TermCount(1));
        Assertions.assertEquals(
            List.of(new TermPos(3), new TermPos(9)),
            InMemoryDocumentTest.positions(doc),
            "Ascending positions"
        );
    }

    @Test
    void termsAreSorted() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("b", new TermCount(1));
        doc.addTerm("a", new TermCount(1));
        Assertions.assertEquals(
            List.of("a", "b"), InMemoryDocumentTest.terms(doc), "Sorted terms"
        );
    }

    @Test
    void termsSortByCodePoint() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("ａ", new TermCount(1));
        doc.addTerm("😀", new TermCount(1));
        Assertions.assertEquals(
            List.of("ａ", "😀"),
            InMemoryDocumentTest.terms(doc),
            "Code point order"
        );
    }

    @Test
    void rejectsEmptyTerm() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().addTerm("", new TermCount(1)),
            "Empty term"
        );
    }

    @Test
    void rejectsEmptyPostingTerm() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().addPosting(
                "", TermPos.FIRST, new TermCount(1)
            ),
            "Empty term"
        );
    }

    @Test
    void removesTerm() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.removeTerm("a");
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Removed");
    }

    @Test
    void removedTermStartsAfresh() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(4));
        doc.removeTerm("a");
        doc.addTerm("a", new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), InMemoryDocumentTest.wdf(doc), "Reset"
        );
    }

    @Test
    void removeMissingTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().removeTerm("a"),
            "Missing term"
        );
    }

    @Test
    void removeEmptyTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().removeTerm(""),
            "Empty term"
        );
    }

    @Test
    void removePostingDecreasesWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(2));
        doc.addPosting("a", new TermPos(2), new TermCount(2));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(3), InMemoryDocumentTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void removePostingSaturatesWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(2), TermCount.ZERO);
        doc.removePosting("a", new TermPos(1), new TermCount(5));
        Assertions.assertEquals(
            TermCount.ZERO, InMemoryDocumentTest.wdf(doc), "Zero wdf"
        );
    }

    @Test
    void removePostingKeepsTermWithPositions() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(2), TermCount.ZERO);
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Term kept"
        );
    }

    @Test
    void removePostingKeepsTermWithWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(3));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Term kept"
        );
    }

    @Test
    void removeLastPostingDropsTerm() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Dropped");
    }

    @Test
    void removePostingOfMissingTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().removePosting(
                "a", TermPos.FIRST, new TermCount(1)
            ),
            "Missing term"
        );
    }

    @Test
    void removePostingOfMissingPositionFails() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> doc.removePosting("a", new TermPos(2), new TermCount(1)),
            "Missing position"
        );
    }

    @Test
    void failedRemovePostingKeepsWdf() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addPosting("a", new TermPos(1), new TermCount(2));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> doc.removePosting("a", new TermPos(2), new TermCount(1)),
            "Missing position"
        );
        Assertions.assertEquals(
            new TermCount(2), InMemoryDocumentTest.wdf(doc), "Wdf kept"
        );
    }

    @Test
    void removePostingOfEmptyTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new InMemoryDocument().removePosting(
                "", TermPos.FIRST, new TermCount(1)
            ),
            "Empty term"
        );
    }

    @Test
    void clearsTerms() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(1));
        doc.addTerm("b", new TermCount(1));
        doc.clearTerms();
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Cleared");
    }

    @Test
    void termListHasNoTermFrequency() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(1));
        try (TermIterator iter = doc.termList()) {
            iter.next();
            Assertions.assertThrows(
                InvalidOperationError.class,
                iter::termFrequency,
                "Detached document"
            );
        }
    }

    @Test
    void termListReadsSnapshot() {
        final InMemoryDocument doc = new InMemoryDocument();
        try (TermIterator iter = doc.termList()) {
            doc.addTerm("a", new TermCount(1));
            Assertions.assertFalse(iter.hasNext(), "Snapshot unchanged");
        }
    }

    @Test
    void termListSkipsForward() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(1));
        doc.addTerm("c", new TermCount(1));
        doc.addTerm("e", new TermCount(1));
        try (TermIterator iter = doc.termList()) {
            iter.skipTo("b");
            Assertions.assertEquals("c", iter.next(), "First at or after");
        }
    }

    @Test
    void closedTermListEnds() {
        final InMemoryDocument doc = new InMemoryDocument();
        doc.addTerm("a", new TermCount(1));
        final TermIterator iter = doc.termList();
        iter.close();
        Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
    }

    private static List<String> terms(final InMemoryDocument doc) {
        final List<String> result = new ArrayList<>(2);
        try (TermIterator iter = doc.termList()) {
            while (iter.hasNext()) {
                result.add(iter.next());
            }
        }
        return result;
    }

    private static TermCount wdf(final InMemoryDocument doc) {
        try (TermIterator iter = doc.termList()) {
            iter.next();
            return iter.wdf();
        }
    }

    private static TermCount count(final InMemoryDocument doc) {
        try (TermIterator iter = doc.termList()) {
            iter.next();
            return iter.positionCount();
        }
    }

    private static List<TermPos> positions(final InMemoryDocument doc) {
        final List<TermPos> result = new ArrayList<>(2);
        try (TermIterator terms = doc.termList()) {
            terms.next();
            try (PositionIterator iter = terms.positions()) {
                while (iter.hasNext()) {
                    result.add(iter.next());
                }
            }
        }
        return result;
    }
}
