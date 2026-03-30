/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link EmptyShard}.
 *
 * @since 0.1.0
 */
final class EmptyShardTest {

    @Test
    void hasZeroStatistics() {
        final Shard shard = new EmptyShard();
        Assertions.assertEquals(
            List.of(
                TermCount.ZERO, DocId.NONE, TotalLength.ZERO,
                TermFrequencies.NONE, false, false, TermCount.ZERO
            ),
            List.of(
                shard.docCount(), shard.lastDocId(), shard.totalLength(),
                shard.frequencies("a"), shard.termExists("a"),
                shard.hasPositions(), shard.size()
            ),
            "Nothing counted"
        );
    }

    @Test
    void hasZeroBounds() {
        final Shard shard = new EmptyShard();
        Assertions.assertEquals(
            List.of(
                ValueStats.NONE, TermCount.ZERO, TermCount.ZERO,
                TermCount.ZERO, TermCount.ZERO, TermCount.ZERO
            ),
            List.of(
                shard.valueStats(new ValueNo(1L)),
                shard.docLengthLowerBound(),
                shard.docLengthUpperBound(),
                shard.wdfUpperBound("a"),
                shard.uniqueTermsLowerBound(),
                shard.uniqueTermsUpperBound()
            ),
            "Nothing bounded"
        );
    }

    @Test
    void opensEmptyLists() {
        final Shard shard = new EmptyShard();
        Assertions.assertEquals(
            List.of(true, true, true, true, true, true, true, true, true),
            List.of(
                shard.openPostList("a").atEnd(),
                shard.openLeafPostList("a", true).isEmpty(),
                shard.openAllTerms("").atEnd(),
                shard.openMetadataKeys("").atEnd(),
                shard.openSpellingTermList("a").atEnd(),
                shard.openSpellingWordList().atEnd(),
                shard.openSynonyms("a").atEnd(),
                shard.openSynonymKeys("").atEnd(),
                shard.openValueList(new ValueNo(0L)).atEnd()
            ),
            "Every list empty"
        );
    }

    @Test
    void storesNothing() {
        final Shard shard = new EmptyShard();
        Assertions.assertEquals(
            List.of(TermCount.ZERO, ""),
            List.of(shard.spellingFrequency("a"), shard.metadata("k")),
            "No spelling, no metadata"
        );
    }

    @Test
    void hasIdentity() {
        final Shard shard = new EmptyShard();
        Assertions.assertEquals(
            List.of(
                new Revision(0L), true, false, false, "", TransactionState.NONE
            ),
            List.of(
                shard.revision(), shard.uuid().isEmpty(), shard.locked(),
                shard.reopen(), shard.description(),
                shard.transactions().state()
            ),
            "Revision zero, no identifier"
        );
    }

    @Test
    void closesAndCancelsQuietly() {
        final Shard shard = new EmptyShard();
        shard.cancel();
        shard.close();
        Assertions.assertEquals(
            TransactionState.NONE, shard.transactions().state(), "Unchanged"
        );
    }

    @Test
    void rejectsDocumentAccess() {
        final Shard shard = new EmptyShard();
        final DocId one = new DocId(1);
        Assertions.assertEquals(
            7,
            EmptyShardTest.refused(
                List.of(
                    () -> shard.docLength(one),
                    () -> shard.uniqueTerms(one),
                    () -> shard.wdfDocMax(one),
                    () -> shard.openTermList(one),
                    () -> shard.openTermListDirect(one),
                    () -> shard.openPositionList(one, "a"),
                    () -> shard.openDocument(one, true)
                )
            ),
            "Every document call refused"
        );
    }

    @Test
    void rejectsWrites() {
        final Shard shard = new EmptyShard();
        final DocumentInternal doc = new DocumentInternal();
        Assertions.assertEquals(
            14,
            EmptyShardTest.refused(
                List.of(
                    () -> shard.addDocument(doc),
                    () -> shard.deleteDocument(new DocId(1)),
                    () -> shard.deleteDocument("a"),
                    () -> shard.replaceDocument(new DocId(1), doc),
                    () -> shard.replaceDocument("a", doc),
                    () -> shard.setMetadata("k", "v"),
                    shard::commit,
                    () -> shard.beginTransaction(false),
                    () -> shard.endTransaction(true),
                    () -> shard.addSpelling("a", new TermCount(1)),
                    () -> shard.removeSpelling("a", new TermCount(1)),
                    () -> shard.addSynonym("a", "b"),
                    () -> shard.removeSynonym("a", "b"),
                    () -> shard.clearSynonyms("a")
                )
            ),
            "Every write refused"
        );
    }

    @Test
    void namesMissingShard() {
        String message = "";
        try {
            new EmptyShard().commit();
        } catch (final InvalidOperationError ex) {
            message = ex.getMessage();
        }
        Assertions.assertEquals("No subdatabases", message, "Message");
    }

    @Test
    void hasNoBackend() {
        Assertions.assertThrows(
            InvariantError.class, new EmptyShard()::backend, "No backend"
        );
    }

    @Test
    void releasesNoDocument() {
        Assertions.assertThrows(
            InvariantError.class,
            () -> new EmptyShard().release(new DocumentInternal()),
            "Nothing to release"
        );
    }

    private static int refused(final List<Runnable> calls) {
        int count = 0;
        for (final Runnable call : calls) {
            try {
                call.run();
            } catch (final InvalidOperationError ex) {
                count += 1;
            }
        }
        return count;
    }
}
