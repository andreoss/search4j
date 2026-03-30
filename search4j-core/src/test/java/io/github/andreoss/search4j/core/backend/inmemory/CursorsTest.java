/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the iterator building blocks of the in-memory backend.
 *
 * @since 0.1.0
 */
final class CursorsTest {

    @Test
    void postingEndRaises() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("cherry")
        ) {
            iter.next();
            Assertions.assertThrows(
                NoSuchElementException.class, iter::next, "At the end"
            );
        }
    }

    @Test
    void postingAccessorBeforeStartFails() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("cherry")
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class, iter::wdf, "Not started"
            );
        }
    }

    @Test
    void everyDocumentWdfBeforeStartFails() {
        try (
            WritableDatabase db = Corpus.build();
            PostingIterator iter = db.postings("")
        ) {
            Assertions.assertThrows(
                InvalidOperationError.class, iter::wdf, "Not started"
            );
        }
    }

    @Test
    void termSkipNeverGoesBackwards() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            iter.skipTo("a");
            Assertions.assertEquals("banana", iter.next(), "Forward only");
        }
    }

    @Test
    void termSkipToCurrentStays() {
        try (
            WritableDatabase db = Corpus.build();
            TermIterator iter = db.termList(DocId.of(1))
        ) {
            iter.next();
            iter.skipTo("apple");
            Assertions.assertEquals("banana", iter.next(), "Past current");
        }
    }
}
