/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

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
 * Tests of {@link DocumentInternal}.
 *
 * @since 0.1.0
 */
final class DocumentInternalTest {

    @Test
    void startsWithEmptyData() {
        Assertions.assertEquals(
            "", new DocumentInternal().data(), "Empty data"
        );
    }

    @Test
    void storesData() {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData("text");
        Assertions.assertEquals("text", doc.data(), "Data read back");
    }

    @Test
    void storesValue() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        Assertions.assertEquals(
            Optional.of("v"), doc.value(new ValueNo(1L)), "Value read back"
        );
    }

    @Test
    void emptyValueClearsSlot() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(1L), "");
        Assertions.assertEquals(
            Optional.empty(), doc.value(new ValueNo(1L)), "Slot cleared"
        );
    }

    @Test
    void removesValue() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        doc.removeValue(new ValueNo(1L));
        Assertions.assertEquals(
            TermCount.ZERO, doc.valueCount(), "Value removed"
        );
    }

    @Test
    void clearsValues() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(2L), "w");
        doc.clearValues();
        Assertions.assertEquals(
            TermCount.ZERO, doc.valueCount(), "Values cleared"
        );
    }

    @Test
    void countsValues() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        doc.addValue(new ValueNo(2L), "w");
        Assertions.assertEquals(
            new TermCount(2), doc.valueCount(), "Value count"
        );
    }

    @Test
    void iteratesValuesBySlot() {
        final DocumentInternal doc = new DocumentInternal();
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
        final DocumentInternal doc = new DocumentInternal();
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
        try (ValueIterator iter = new DocumentInternal().values()) {
            Assertions.assertThrows(
                InvalidOperationError.class,
                () -> iter.skipTo(DocId.of(1)),
                "No skip by document"
            );
        }
    }

    @Test
    void closedValueIteratorEnds() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        final ValueIterator iter = doc.values();
        iter.close();
        Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
    }

    @Test
    void valueIteratorReadsSnapshot() {
        final DocumentInternal doc = new DocumentInternal();
        try (ValueIterator iter = doc.values()) {
            doc.addValue(new ValueNo(1L), "v");
            Assertions.assertFalse(iter.hasNext(), "Snapshot unchanged");
        }
    }

    @Test
    void addTermAccumulatesWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(2));
        doc.addTerm("a", new TermCount(3));
        Assertions.assertEquals(
            new TermCount(5), DocumentInternalTest.wdf(doc), "Wdf sum"
        );
    }

    @Test
    void zeroWdfTermIsKept() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", TermCount.ZERO);
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Zero wdf term kept"
        );
    }

    @Test
    void addPostingIncreasesWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(4), new TermCount(2));
        Assertions.assertEquals(
            new TermCount(3), DocumentInternalTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void repeatedPositionStillIncreasesWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(2), DocumentInternalTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void repeatedPositionKeptOnce() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1),
            DocumentInternalTest.count(doc),
            "One position"
        );
    }

    @Test
    void positionsAreSorted() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(9), new TermCount(1));
        doc.addPosting("a", new TermPos(3), new TermCount(1));
        Assertions.assertEquals(
            List.of(new TermPos(3), new TermPos(9)),
            DocumentInternalTest.positions(doc),
            "Ascending positions"
        );
    }

    @Test
    void termsAreSorted() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("b", new TermCount(1));
        doc.addTerm("a", new TermCount(1));
        Assertions.assertEquals(
            List.of("a", "b"), DocumentInternalTest.terms(doc), "Sorted terms"
        );
    }

    @Test
    void termsSortByCodePoint() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("ａ", new TermCount(1));
        doc.addTerm("😀", new TermCount(1));
        Assertions.assertEquals(
            List.of("ａ", "😀"),
            DocumentInternalTest.terms(doc),
            "Code point order"
        );
    }

    @Test
    void rejectsEmptyTerm() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().addTerm("", new TermCount(1)),
            "Empty term"
        );
    }

    @Test
    void rejectsEmptyPostingTerm() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().addPosting(
                "", TermPos.FIRST, new TermCount(1)
            ),
            "Empty term"
        );
    }

    @Test
    void removesTerm() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.removeTerm("a");
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Removed");
    }

    @Test
    void removedTermStartsAfresh() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(4));
        doc.removeTerm("a");
        doc.addTerm("a", new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), DocumentInternalTest.wdf(doc), "Reset"
        );
    }

    @Test
    void removeMissingTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().removeTerm("a"),
            "Missing term"
        );
    }

    @Test
    void removeEmptyTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().removeTerm(""),
            "Empty term"
        );
    }

    @Test
    void removePostingDecreasesWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(2));
        doc.addPosting("a", new TermPos(2), new TermCount(2));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(3), DocumentInternalTest.wdf(doc), "Wdf"
        );
    }

    @Test
    void removePostingKeepsWdfAboveTheSignedRange() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", TermCount.parse("3000000000"));
        doc.addPosting("a", new TermPos(1), TermCount.ZERO);
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            List.of(TermCount.parse("2999999999"), new TermCount(1)),
            List.of(DocumentInternalTest.wdf(doc), doc.termCount()),
            "Unsigned wdf decreased by one, term kept"
        );
    }

    @Test
    void addTermWrapsWdfAsTheReference() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", TermCount.parse("3000000000"));
        doc.addTerm("a", TermCount.parse("3000000000"));
        Assertions.assertEquals(
            TermCount.parse("1705032704"), DocumentInternalTest.wdf(doc),
            "Sum of increments modulo two to the 32"
        );
    }

    @Test
    void removePostingSaturatesWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(2), TermCount.ZERO);
        doc.removePosting("a", new TermPos(1), new TermCount(5));
        Assertions.assertEquals(
            TermCount.ZERO, DocumentInternalTest.wdf(doc), "Zero wdf"
        );
    }

    @Test
    void removePostingKeepsTermWithPositions() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.addPosting("a", new TermPos(2), TermCount.ZERO);
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Term kept"
        );
    }

    @Test
    void removePostingKeepsTermWithWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(3));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(
            new TermCount(1), doc.termCount(), "Term kept"
        );
    }

    @Test
    void removeLastPostingDropsTerm() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.removePosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Dropped");
    }

    @Test
    void removePostingOfMissingTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().removePosting(
                "a", TermPos.FIRST, new TermCount(1)
            ),
            "Missing term"
        );
    }

    @Test
    void removePostingOfMissingPositionFails() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> doc.removePosting("a", new TermPos(2), new TermCount(1)),
            "Missing position"
        );
    }

    @Test
    void failedRemovePostingKeepsWdf() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(2));
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> doc.removePosting("a", new TermPos(2), new TermCount(1)),
            "Missing position"
        );
        Assertions.assertEquals(
            new TermCount(2), DocumentInternalTest.wdf(doc), "Wdf kept"
        );
    }

    @Test
    void removePostingOfEmptyTermFails() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new DocumentInternal().removePosting(
                "", TermPos.FIRST, new TermCount(1)
            ),
            "Empty term"
        );
    }

    @Test
    void clearsTerms() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        doc.addTerm("b", new TermCount(1));
        doc.clearTerms();
        Assertions.assertEquals(TermCount.ZERO, doc.termCount(), "Cleared");
    }

    @Test
    void termListHasNoTermFrequency() {
        final DocumentInternal doc = new DocumentInternal();
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
        final DocumentInternal doc = new DocumentInternal();
        try (TermIterator iter = doc.termList()) {
            doc.addTerm("a", new TermCount(1));
            Assertions.assertFalse(iter.hasNext(), "Snapshot unchanged");
        }
    }

    @Test
    void termListSkipsForward() {
        final DocumentInternal doc = new DocumentInternal();
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
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        final TermIterator iter = doc.termList();
        iter.close();
        Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
    }

    @Test
    void termListHasNoWdfBeforeStart() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        try (TermIterator iter = doc.termList()) {
            Assertions.assertEquals(
                "Iteration has not started",
                Assertions.assertThrows(
                    InvalidOperationError.class, iter::wdf, "Not started"
                ).getMessage(),
                "Refused by the iterator"
            );
        }
    }

    @Test
    void valueIteratorHasNoSlotBeforeStart() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addValue(new ValueNo(1L), "v");
        try (ValueIterator iter = doc.values()) {
            Assertions.assertEquals(
                "Iteration has not started",
                Assertions.assertThrows(
                    InvalidOperationError.class, iter::slot, "Not started"
                ).getMessage(),
                "Refused by the iterator"
            );
        }
    }

    @Test
    void closedPositionIteratorEnds() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        try (TermIterator terms = doc.termList()) {
            terms.next();
            final PositionIterator iter = terms.positions();
            iter.close();
            Assertions.assertFalse(iter.hasNext(), "Closed iterator ends");
        }
    }

    private static List<String> terms(final DocumentInternal doc) {
        final List<String> result = new ArrayList<>(2);
        try (TermIterator iter = doc.termList()) {
            while (iter.hasNext()) {
                result.add(iter.next());
            }
        }
        return result;
    }

    private static TermCount wdf(final DocumentInternal doc) {
        try (TermIterator iter = doc.termList()) {
            iter.next();
            return iter.wdf();
        }
    }

    private static TermCount count(final DocumentInternal doc) {
        try (TermIterator iter = doc.termList()) {
            iter.next();
            return iter.positionCount();
        }
    }

    private static List<TermPos> positions(final DocumentInternal doc) {
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
