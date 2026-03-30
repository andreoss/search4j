/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the edit tracking of {@link DocumentInternal} and its lists.
 *
 * <p>The stored document holds values "b" and "x" in slots 1 and 2,
 * "apple" at positions 1 and 5 and "banana" with frequency 3.</p>
 *
 * @since 0.1.0
 */
final class DocumentStateTest {

    @Test
    void hasNoIdentifierStandalone() {
        Assertions.assertEquals(
            DocId.NONE, new DocumentInternal().docId(), "Standalone"
        );
    }

    @Test
    void keepsStoredIdentifier() {
        Assertions.assertEquals(
            new DocId(1), DocumentStateTest.stored().docId(), "Stored"
        );
    }

    @Test
    void startsUnmodified() {
        Assertions.assertEquals(
            List.of(false, false, false, false),
            DocumentStateTest.flags(DocumentStateTest.stored()),
            "Nothing edited"
        );
    }

    @Test
    void tracksDataEdit() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.setData("new");
        Assertions.assertEquals(
            List.of(true, false, false, false),
            DocumentStateTest.flags(doc),
            "Data edited"
        );
    }

    @Test
    void tracksTermEdit() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.addTerm("pear", new TermCount(1));
        Assertions.assertEquals(
            List.of(false, true, false, false),
            DocumentStateTest.flags(doc),
            "Terms edited"
        );
    }

    @Test
    void tracksValueEdit() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.addValue(new ValueNo(3L), "v");
        Assertions.assertEquals(
            List.of(false, false, true, false),
            DocumentStateTest.flags(doc),
            "Values edited"
        );
    }

    @Test
    void tracksPositionEdit() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.addPosting("pear", new TermPos(9), new TermCount(1));
        Assertions.assertEquals(
            List.of(false, true, false, true),
            DocumentStateTest.flags(doc),
            "Positions edited"
        );
    }

    @Test
    void tracksRemovalOfPositionedTerm() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.removeTerm("apple");
        Assertions.assertTrue(doc.positionsModified(), "Positions dropped");
    }

    @Test
    void ignoresRemovalOfPlainTerm() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.removeTerm("banana");
        Assertions.assertFalse(doc.positionsModified(), "No position lost");
    }

    @Test
    void tracksClearOfStoredValues() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.clearValues();
        Assertions.assertEquals(
            List.of(true, TermCount.ZERO),
            List.of(doc.valuesModified(), doc.valueCount()),
            "Cleared"
        );
    }

    @Test
    void ignoresClearOfStandaloneValues() {
        final DocumentInternal doc = new DocumentInternal();
        doc.clearValues();
        Assertions.assertFalse(doc.valuesModified(), "Nothing to clear");
    }

    @Test
    void tracksClearOfStoredTerms() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.clearTerms();
        Assertions.assertEquals(
            List.of(true, true, TermCount.ZERO),
            List.of(
                doc.termsModified(), doc.positionsModified(), doc.termCount()
            ),
            "Cleared"
        );
    }

    @Test
    void ignoresClearOfStandaloneTerms() {
        final DocumentInternal doc = new DocumentInternal();
        doc.clearTerms();
        Assertions.assertFalse(doc.termsModified(), "Nothing to clear");
    }

    @Test
    void clearsEditedTerms() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        doc.clearTerms();
        Assertions.assertEquals(
            List.of(), DocumentStateTest.terms(doc.openTermList()), "Empty"
        );
    }

    @Test
    void countsStoredValues() {
        Assertions.assertEquals(
            new TermCount(2), DocumentStateTest.stored().valueCount(), "Two"
        );
    }

    @Test
    void countsStoredTermsUnfetched() {
        Assertions.assertEquals(
            new TermCount(2), DocumentStateTest.stored().termCount(), "Two"
        );
    }

    @Test
    void countsAddedTermAfterFetch() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.addTerm("cherry", new TermCount(1));
        doc.addTerm("cherry", new TermCount(1));
        Assertions.assertEquals(new TermCount(3), doc.termCount(), "Three");
    }

    @Test
    void countsRevivedPosting() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addPosting("a", new TermPos(1), new TermCount(1));
        doc.removeTerm("a");
        doc.addPosting("a", new TermPos(2), new TermCount(1));
        Assertions.assertEquals(new TermCount(1), doc.termCount(), "Revived");
    }

    @Test
    void countsRevivedTerm() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        doc.removeTerm("a");
        doc.addTerm("a", new TermCount(1));
        Assertions.assertEquals(new TermCount(1), doc.termCount(), "Revived");
    }

    @Test
    void opensStoredTerms() {
        Assertions.assertEquals(
            List.of("apple", "banana"),
            DocumentStateTest.terms(DocumentStateTest.stored().openTermList()),
            "Stored terms"
        );
    }

    @Test
    void opensEditedTerms() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.removeTerm("apple");
        doc.addTerm("cherry", new TermCount(1));
        Assertions.assertEquals(
            List.of("banana", "cherry"),
            DocumentStateTest.terms(doc.openTermList()),
            "Edited terms"
        );
    }

    @Test
    void opensNoTermsStandalone() {
        Assertions.assertTrue(
            new DocumentInternal().openTermList().next().atEnd(), "None"
        );
    }

    @Test
    void describesStandalone() {
        Assertions.assertEquals(
            "Document(docid=0)", new DocumentInternal().description(), "Bare"
        );
    }

    @Test
    void describesCachedParts() {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData("text");
        doc.addTerm("a", new TermCount(1));
        doc.addValue(new ValueNo(1L), "v");
        Assertions.assertEquals(
            "Document(docid=0, data=text, terms[1], values[1])",
            doc.description(),
            "Every part"
        );
    }

    @Test
    void sizesTermListByLiveTerms() {
        final DocumentInternal doc = new DocumentInternal();
        doc.addTerm("a", new TermCount(1));
        doc.addTerm("b", new TermCount(1));
        doc.removeTerm("a");
        Assertions.assertEquals(
            new TermCount(1), doc.openTermList().approxSize(), "One live"
        );
    }

    @Test
    void skipsTermListForward() {
        final TermList list = DocumentStateTest.list("a", "c", "e");
        final List<String> seen = new ArrayList<>(0);
        for (final String target : new String[] {"b", "a", "c", "d"}) {
            list.skipTo(target);
            seen.add(list.term());
        }
        Assertions.assertEquals(
            List.of("c", "c", "c", "e"), seen, "Forward only"
        );
    }

    @Test
    void skipsTermListPastEnd() {
        Assertions.assertTrue(
            DocumentStateTest.list("a").skipTo("b").atEnd(), "Past end"
        );
    }

    @Test
    void holdsTermPositions() {
        final DocumentInternal doc = DocumentStateTest.stored();
        doc.addTerm("zzz", new TermCount(1));
        Assertions.assertEquals(
            List.of(new TermPos(1), new TermPos(5)),
            doc.openTermList().next().heldPositions().orElseThrow(),
            "Positions held"
        );
    }

    @Test
    void describesValueList() {
        final NavigableMap<ValueNo, String> values = new TreeMap<>();
        values.put(new ValueNo(4L), "v");
        final DocumentValueList list = new DocumentValueList(values);
        final List<String> seen = new ArrayList<>(0);
        list.next();
        seen.add(list.description());
        list.next();
        seen.add(list.description());
        Assertions.assertEquals(
            List.of(
                "DocumentValueList(slot=4, value=\"v\")",
                "DocumentValueList(atend)"
            ),
            seen,
            "On a value, then at end"
        );
    }

    private static List<Boolean> flags(final DocumentInternal doc) {
        return List.of(
            doc.dataModified(),
            doc.termsModified(),
            doc.valuesModified(),
            doc.positionsModified()
        );
    }

    private static List<String> terms(final TermList start) {
        final List<String> result = new ArrayList<>(0);
        TermList list = start.next();
        while (!list.atEnd()) {
            result.add(list.term());
            list = list.next();
        }
        return result;
    }

    private static TermList list(final String... terms) {
        final DocumentInternal doc = new DocumentInternal();
        for (final String term : terms) {
            doc.addTerm(term, new TermCount(1));
        }
        return doc.openTermList();
    }

    private static DocumentInternal stored() {
        final InMemoryShard shard = new InMemoryShard();
        final DocumentInternal doc = new DocumentInternal();
        doc.setData("one");
        doc.addValue(new ValueNo(1L), "b");
        doc.addValue(new ValueNo(2L), "x");
        doc.addPosting("apple", new TermPos(5), new TermCount(1));
        doc.addPosting("apple", new TermPos(1), new TermCount(1));
        doc.addTerm("banana", new TermCount(3));
        shard.addDocument(doc);
        return shard.openDocument(new DocId(1), true).orElseThrow();
    }
}
