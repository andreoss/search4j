/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Revision;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.TransactionState;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseModifiedError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link VidrioShard}, the read-only shard.
 *
 * @since 0.1.0
 */
final class VidrioShardTest {

    @Test
    void reportsIdentityAndRevision(@TempDir final Path dir) {
        final VidrioWritableShard writer = VidrioShardTest.filled(dir, 3);
        writer.close();
        try (VidrioShard shard = VidrioShard.open(dir)) {
            Assertions.assertEquals(
                List.of(
                    new Revision(1L), writer.uuid().orElseThrow(), Backend.VIDRIO,
                    String.format("Vidrio(%s)", dir), new TermCount(1),
                    TransactionState.READ_ONLY, false,
                    new DocRange(DocId.of(1), DocId.of(3)), new TermCount(3)
                ),
                List.of(
                    shard.revision(), shard.uuid().orElseThrow(), shard.backend(),
                    shard.description(), shard.size(),
                    shard.transactions().state(), shard.locked(),
                    shard.usedDocIds(), shard.docCount()
                ),
                "Read-only shard over a committed database"
            );
        }
    }

    @Test
    void readsEverythingFromTables(@TempDir final Path dir) {
        VidrioShardTest.filled(dir, 2).close();
        try (VidrioShard shard = VidrioShard.open(dir)) {
            final DocumentInternal doc = shard.openDocument(DocId.of(2), false)
                .orElseThrow();
            Assertions.assertEquals(
                List.of(
                    new TermFrequencies(new TermCount(2), new TermCount(2)), true,
                    false, true, new TermCount(2), new TermCount(2),
                    new TermCount(1), new TermCount(1), "data 2", "v2",
                    new TermCount(2), new TermCount(2), new TermCount(2),
                    "meta", true, true, DocId.of(2)
                ),
                List.of(
                    shard.frequencies("every"), shard.termExists("every"),
                    shard.termExists("none"), shard.hasPositions(),
                    shard.docLength(DocId.of(1)), shard.uniqueTerms(DocId.of(1)),
                    shard.wdfDocMax(DocId.of(1)), shard.wdfUpperBound("every"),
                    doc.data(), doc.value(new ValueNo(1L)).orElseThrow(),
                    shard.valueStats(new ValueNo(1L)).frequency(),
                    shard.docLengthUpperBound(), shard.docLengthLowerBound(),
                    shard.metadata("key"),
                    shard.openLeafPostList("", false).isPresent(),
                    shard.openLeafPostList("every", true).isPresent(),
                    shard.openPostList("doc2").next().docId()
                ),
                "Statistics, bounds, lists and documents"
            );
        }
    }

    @Test
    void longTermsAreUnknown(@TempDir final Path dir) {
        VidrioShardTest.filled(dir, 1).close();
        final String term = "x".repeat(300);
        try (VidrioShard shard = VidrioShard.open(dir)) {
            Assertions.assertEquals(
                List.of(TermFrequencies.NONE, false, TermCount.ZERO, false, true),
                List.of(
                    shard.frequencies(term), shard.termExists(term),
                    shard.wdfUpperBound(term),
                    shard.openLeafPostList(term, false).isPresent(),
                    shard.openPostList(term).atEnd()
                ),
                "No lookup for a term the format cannot store"
            );
        }
    }

    @Test
    void emptyDatabaseHasNoLists(@TempDir final Path dir) {
        VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        ).close();
        try (VidrioShard shard = VidrioShard.open(dir)) {
            Assertions.assertEquals(
                List.of(
                    false, true, false, new DocRange(DocId.of(1), DocId.NONE),
                    TermCount.ZERO, true, new Revision(0L)
                ),
                List.of(
                    shard.openLeafPostList("", false).isPresent(),
                    shard.openPostList("").atEnd(), shard.hasPositions(),
                    shard.usedDocIds(), shard.uniqueTermsLowerBound(),
                    shard.openDocument(DocId.of(1), true).isPresent(),
                    shard.revision()
                ),
                "Nothing to list, lazy documents always open"
            );
            Assertions.assertThrows(
                DocNotFoundError.class,
                () -> shard.openDocument(DocId.of(1), false),
                "Eager document checked"
            );
        }
    }

    @Test
    void reopenMovesToTheLatestRevision(@TempDir final Path dir) {
        try (VidrioWritableShard writer = VidrioShardTest.filled(dir, 1)) {
            try (VidrioShard shard = VidrioShard.open(dir)) {
                final boolean same = shard.reopen();
                writer.addDocument(VidrioShardTest.document(2));
                writer.commit();
                Assertions.assertEquals(
                    List.of(
                        false, new TermCount(1), true, new TermCount(2),
                        new Revision(2L)
                    ),
                    List.of(
                        same, shard.docCount(), shard.reopen(), shard.docCount(),
                        shard.revision()
                    ),
                    "Reopened at revision 2"
                );
            }
        }
    }

    @Test
    void readerSeesDiscardedRevisionAsModified(@TempDir final Path dir) {
        try (VidrioWritableShard writer = VidrioShardTest.filled(dir, 400)) {
            try (VidrioShard shard = VidrioShard.open(dir)) {
                for (int round = 0; round < 3; ++round) {
                    for (int idx = 0; idx < 400; ++idx) {
                        writer.addDocument(VidrioShardTest.document(idx + 1));
                    }
                    writer.commit();
                }
                Assertions.assertThrows(
                    DatabaseModifiedError.class,
                    () -> {
                        for (int idx = 1; idx <= 400; ++idx) {
                            shard.openTermList(DocId.of(idx)).next();
                        }
                    },
                    "Reference databasemodified1"
                );
                shard.reopen();
                Assertions.assertEquals(
                    new TermCount(1600), shard.docCount(), "Latest revision"
                );
            }
        }
    }

    @Test
    void closeIsFinal(@TempDir final Path dir) {
        VidrioShardTest.filled(dir, 1).close();
        final VidrioShard shard = VidrioShard.open(dir);
        shard.close();
        shard.close();
        shard.release(new DocumentInternal());
        Assertions.assertAll(
            "Closed shard",
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, shard::reopen, "reopen"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, shard::docCount, "count"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> shard.relock(true), "relock"
            ),
            () -> Assertions.assertEquals(
                new Revision(1L), shard.revision(), "Revision still known"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class, shard::commit, "commit"
            ),
            () -> Assertions.assertThrows(
                InvalidOperationError.class, () -> shard.beginTransaction(true),
                "transaction"
            )
        );
    }

    @Test
    void relocksForWriting(@TempDir final Path dir) {
        VidrioShardTest.filled(dir, 1).close();
        try (VidrioShard shard = VidrioShard.open(dir)) {
            try (
                VidrioWritableShard writable =
                    (VidrioWritableShard) shard.relock(true)
            ) {
                Assertions.assertEquals(
                    List.of(true, true, true, TransactionState.NONE),
                    List.of(
                        shard.relock(false) == shard, writable.locked(),
                        shard.locked(),
                        writable.transactions().state()
                    ),
                    "Writable shard over the same directory"
                );
            }
        }
    }

    @Test
    void rejectsMissingDatabase(@TempDir final Path dir) {
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> VidrioShard.open(dir.resolve("absent")),
            "No version file"
        );
    }

    /**
     * Creates a database with numbered documents, each holding the term
     * {@code every}, a positioned term of its own, a value and data.
     *
     * @param dir Database directory
     * @param docs Number of documents
     * @return Writable shard, committed
     */
    static VidrioWritableShard filled(final Path dir, final int docs) {
        final VidrioWritableShard shard = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        );
        for (int idx = 1; idx <= docs; ++idx) {
            shard.addDocument(VidrioShardTest.document(idx));
        }
        shard.setMetadata("key", "meta");
        shard.commit();
        return shard;
    }

    /**
     * A numbered document.
     *
     * @param number Document number
     * @return Document
     */
    static DocumentInternal document(final int number) {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData(String.format("data %d", number));
        doc.addValue(new ValueNo(1L), String.format("v%d", number));
        doc.addTerm("every", new TermCount(1));
        doc.addPosting(
            String.format("doc%d", number), new TermPos(number), new TermCount(1)
        );
        return doc;
    }
}
