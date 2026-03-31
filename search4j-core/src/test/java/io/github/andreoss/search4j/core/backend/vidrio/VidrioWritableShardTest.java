/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Revision;
import io.github.andreoss.search4j.core.backend.TransactionState;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DatabaseCreateError;
import io.github.andreoss.search4j.core.error.DatabaseLockError;
import io.github.andreoss.search4j.core.error.DatabaseNotFoundError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DbFlag;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of {@link VidrioWritableShard} and {@link WriteSession}.
 *
 * @since 0.1.0
 */
final class VidrioWritableShardTest {

    @Test
    void flushesAndCommitsAtThreshold(@TempDir final Path dir) {
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code()).withThreshold(3)
            )
        ) {
            shard.addDocument(VidrioShardTest.document(1));
            shard.addDocument(VidrioShardTest.document(2));
            final Revision before = shard.revision();
            shard.addDocument(VidrioShardTest.document(3));
            final Revision after = shard.revision();
            shard.beginTransaction(false);
            for (int idx = 4; idx <= 7; ++idx) {
                shard.addDocument(VidrioShardTest.document(idx));
            }
            final Revision inside = shard.revision();
            shard.endTransaction(false);
            Assertions.assertEquals(
                List.of(
                    new Revision(0L), new Revision(1L), new Revision(1L),
                    new TermCount(3), true, false
                ),
                List.of(
                    before, after, inside, shard.docCount(),
                    shard.termExists("doc3"), shard.termExists("doc5")
                ),
                "Threshold commits outside a transaction, flushes inside"
            );
        }
    }

    @Test
    void reportsIdentityAndLock(@TempDir final Path dir) {
        final VidrioWritableShard shard = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE_OR_OPEN.code())
        );
        shard.addDocument(VidrioShardTest.document(1));
        final List<Object> open = List.of(
            shard.locked(), shard.revision(), shard.uuid().isPresent(),
            shard.backend(), shard.description(), shard.size(),
            shard.transactions().state(), shard.usedDocIds(),
            shard.relock(true) == shard
        );
        shard.close();
        Assertions.assertEquals(
            List.of(
                true, new Revision(0L), true, Backend.VIDRIO,
                String.format("Vidrio(writable, %s)", dir), new TermCount(1),
                TransactionState.NONE, new DocRange(DocId.of(1), DocId.of(1)),
                true, false, new Revision(1L)
            ),
            List.of(
                open.get(0), open.get(1), open.get(2), open.get(3), open.get(4),
                open.get(5), open.get(6), open.get(7), open.get(8),
                shard.locked(), shard.revision()
            ),
            "Lock held until closed, close commits"
        );
    }

    @Test
    void relocksForReading(@TempDir final Path dir) {
        final VidrioWritableShard shard = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        );
        shard.addDocument(VidrioShardTest.document(1));
        try (VidrioShard reader = (VidrioShard) shard.relock(false)) {
            Assertions.assertEquals(
                List.of(new TermCount(1), false, TransactionState.READ_ONLY),
                List.of(
                    reader.docCount(), shard.locked(), reader.transactions().state()
                ),
                "Committed, unlocked and reopened to read"
            );
        }
    }

    @Test
    void overlaysPendingPositionsAndTerms(@TempDir final Path dir) {
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code())
            )
        ) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addPosting("apple", new TermPos(3), new TermCount(1));
            doc.addPosting("apple", new TermPos(9), new TermCount(1));
            doc.addTerm("plain", new TermCount(2));
            shard.addDocument(doc);
            final PositionList positions = shard.openPositionList(DocId.of(1), "apple");
            positions.next();
            final List<Object> pending = List.of(
                shard.hasPositions(), positions.position(),
                shard.openPositionList(DocId.of(1), "plain").next(),
                shard.termExists("apple"), shard.frequencies("plain").collection(),
                shard.openAllTerms("ap").next().term(), shard.docLength(DocId.of(1)),
                shard.uniqueTerms(DocId.of(1)), shard.wdfDocMax(DocId.of(1)),
                shard.openTermList(DocId.of(1)).next().termFrequency()
            );
            shard.deleteDocument(DocId.of(1));
            final boolean quirk = shard.hasPositions();
            shard.commit();
            Assertions.assertEquals(
                List.of(
                    List.of(
                        true, new TermPos(3), false, true, new TermCount(2), "apple",
                        new TermCount(4), new TermCount(2), new TermCount(2),
                        new TermCount(1)
                    ),
                    true, false, false
                ),
                List.of(
                    pending, quirk, shard.hasPositions(), shard.termExists("apple")
                ),
                "Reads see the changes before and after the flush"
            );
        }
    }

    @Test
    void metadataAndValuesReadPending(@TempDir final Path dir) {
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code())
            )
        ) {
            shard.setMetadata("k", "v");
            shard.addDocument(VidrioShardTest.document(1));
            final DocumentInternal doc = shard.openDocument(DocId.of(1), true)
                .orElseThrow();
            final ValueIterator values = doc.values();
            final ValueList list =
                shard.openValueList(new ValueNo(1L));
            list.next();
            Assertions.assertEquals(
                List.of(
                    "v", "k", "v1", "v1", new ValueNo(1L), "data 1",
                    new TermCount(1), DocId.of(1)
                ),
                List.of(
                    shard.metadata("k"), shard.openMetadataKeys("").next().term(),
                    doc.value(new ValueNo(1L)).orElseThrow(), values.next(),
                    values.slot(), doc.data(),
                    shard.valueStats(new ValueNo(1L)).frequency(), list.docId()
                ),
                "Pending metadata, values and data"
            );
        }
    }

    @Test
    void rejectsSecondWriterAndWaitsWhenAsked(@TempDir final Path dir)
        throws Exception {
        final VidrioWritableShard first = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        );
        Assertions.assertThrows(
            DatabaseLockError.class,
            () -> VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.OPEN.code())
            ),
            "Locked"
        );
        final CompletableFuture<VidrioWritableShard> second =
            CompletableFuture.supplyAsync(
                () -> VidrioWritableShard.open(
                    dir,
                    VidrioOptions.of(DbFlag.RETRY_LOCK.add(DatabaseAction.OPEN.code()))
                )
            );
        first.close();
        try (VidrioWritableShard shard = second.get(30L, TimeUnit.SECONDS)) {
            Assertions.assertTrue(shard.locked(), "Retry obtained the lock");
        }
    }

    @Test
    void createRejectsExistingAndOpenRejectsMissing(@TempDir final Path dir) {
        VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        ).close();
        Assertions.assertThrows(
            DatabaseCreateError.class,
            () -> VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code())
            ),
            "Exists already"
        );
        Assertions.assertThrows(
            DatabaseNotFoundError.class,
            () -> VidrioWritableShard.open(
                dir.resolve("absent"), VidrioOptions.of(DatabaseAction.OPEN.code())
            ),
            "Nothing to open"
        );
    }

    @Test
    void overwriteStartsAfresh(@TempDir final Path dir) {
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code())
            )
        ) {
            shard.addDocument(VidrioShardTest.document(1));
        }
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE_OR_OVERWRITE.code())
            )
        ) {
            Assertions.assertEquals(
                List.of(TermCount.ZERO, new Revision(0L)),
                List.of(shard.docCount(), shard.revision()),
                "Overwritten"
            );
        }
    }

    @Test
    void createRejectsUnwritableDirectory(@TempDir final Path dir) throws IOException {
        final Path file = dir.resolve("file");
        Files.writeString(file, "not a directory");
        Assertions.assertThrows(
            DatabaseCreateError.class,
            () -> VidrioWritableShard.open(
                file.resolve("db"), VidrioOptions.of(DatabaseAction.CREATE.code())
            ),
            "mkdir fails under a file"
        );
    }

    @Test
    void recoversWhenCommitFails(@TempDir final Path dir) throws IOException {
        final VidrioWritableShard shard = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        );
        shard.addDocument(VidrioShardTest.document(1));
        shard.commit();
        shard.addDocument(VidrioShardTest.document(2));
        final Path blocker = dir.resolve(VersionFile.TEMP_NAME);
        Files.createDirectories(blocker.resolve("inside"));
        final Class<?> failure = Assertions.assertThrows(
            DatabaseCreateError.class, shard::commit, "Version file not written"
        ).getClass();
        final List<Object> recovered = List.of(
            shard.docCount(), shard.revision(), shard.termExists("doc1"),
            shard.termExists("doc2")
        );
        Files.delete(blocker.resolve("inside"));
        Files.delete(blocker);
        shard.addDocument(VidrioShardTest.document(3));
        shard.close();
        try (VidrioShard reader = VidrioShard.open(dir)) {
            Assertions.assertEquals(
                List.of(
                    DatabaseCreateError.class,
                    List.of(new TermCount(1), new Revision(1L), true, false),
                    new TermCount(2), new Revision(2L)
                ),
                List.of(failure, recovered, reader.docCount(), reader.revision()),
                "Back at the committed revision, writes continue afterwards"
            );
        }
    }

    @Test
    void closesWhenRecoveryFails(@TempDir final Path dir) throws IOException {
        final VidrioWritableShard shard = VidrioWritableShard.open(
            dir, VidrioOptions.of(DatabaseAction.CREATE.code())
        );
        shard.addDocument(VidrioShardTest.document(1));
        Files.delete(dir.resolve(VidrioFormat.versionFileName()));
        Files.createDirectories(dir.resolve(VersionFile.TEMP_NAME).resolve("inside"));
        Assertions.assertEquals(
            List.of(true, false),
            List.of(
                Assertions.assertThrows(
                    DatabaseCreateError.class, shard::commit, "Nothing to recover from"
                ).getMessage().startsWith("Modifications failed"),
                shard.locked()
            ),
            "Lock released, cause named"
        );
    }

    @Test
    void commitWithoutChangesKeepsRevision(@TempDir final Path dir) {
        try (
            VidrioWritableShard shard = VidrioWritableShard.open(
                dir, VidrioOptions.of(DatabaseAction.CREATE.code())
            )
        ) {
            shard.commit();
            shard.addDocument(VidrioShardTest.document(1));
            shard.commit();
            shard.commit();
            shard.cancel();
            Assertions.assertEquals(
                new Revision(1L), shard.revision(), "Reference getrevision1"
            );
        }
    }
}
