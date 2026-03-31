/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.DatabaseLockError;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.RangeError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests of the writes, transactions and lifecycle of a vidrio database
 * through the public API, ported from the reference writable database
 * tests.
 *
 * @since 0.1.0
 */
final class VidrioWritesTest {

    /**
     * Slot of the reference cancel test.
     */
    private static final ValueNo SLOT = new ValueNo(42L);

    @Test
    void replacesTermsAndPositions(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final DocumentInternal first = new DocumentInternal();
            first.addPosting("foo", new TermPos(1), new TermCount(1));
            first.addPosting("foo", new TermPos(2), new TermCount(1));
            first.addPosting("gone", new TermPos(3), new TermCount(1));
            first.addPosting("bar", new TermPos(4), new TermCount(1));
            first.addPosting("foo", new TermPos(5), new TermCount(1));
            final DocId doc = db.addDocument(first);
            final DocumentInternal second = new DocumentInternal();
            second.addPosting("foo", new TermPos(1), new TermCount(1));
            second.addPosting("pipco", new TermPos(2), new TermCount(1));
            second.addPosting("bar", new TermPos(4), new TermCount(1));
            second.addPosting("foo", new TermPos(5), new TermCount(1));
            db.replaceDocument(doc, second);
            Assertions.assertEquals(
                List.of(
                    DocId.of(1), "bar:1:[4]", "foo:2:[1, 5]", "pipco:1:[2]",
                    false, new TermCount(4)
                ),
                List.of(
                    doc, VidrioWritesTest.term(db, doc, "bar"),
                    VidrioWritesTest.term(db, doc, "foo"),
                    VidrioWritesTest.term(db, doc, "pipco"),
                    db.termExists("gone"), db.docLength(doc)
                ),
                "Reference replacedoc1"
            );
        }
    }

    @Test
    void replacesBeyondLastIdAndBelowIt(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.replaceDocument(DocId.of(5), VidrioWritesTest.plain("five"));
            db.replaceDocument(DocId.of(2), VidrioWritesTest.plain("two"));
            db.commit();
            db.replaceDocument(DocId.of(3), VidrioWritesTest.plain("three"));
            Assertions.assertEquals(
                List.of(
                    new TermCount(3), DocId.of(5), DocId.of(6), "five", "two",
                    "three"
                ),
                List.of(
                    db.docCount(), db.lastDocId(),
                    db.addDocument(VidrioWritesTest.plain("six")),
                    db.document(DocId.of(5)).data(),
                    db.document(DocId.of(2)).data(),
                    db.document(DocId.of(3)).data()
                ),
                "Unused identifiers filled, next one above the last"
            );
        }
    }

    @Test
    void emptyReplacementHasZeroLengthAfterCommit(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            Assertions.assertThrows(
                DocNotFoundError.class, () -> db.docLength(DocId.of(1)),
                "Absent before"
            );
            db.replaceDocument(DocId.of(1), new DocumentInternal());
            db.commit();
            Assertions.assertEquals(
                List.of(TermCount.ZERO, TermCount.ZERO, TermCount.ZERO),
                List.of(
                    db.docLength(DocId.of(1)), db.uniqueTerms(DocId.of(1)),
                    db.wdfDocMax(DocId.of(1))
                ),
                "Reference doclenaftercommit1"
            );
        }
    }

    @Test
    void valuesSurviveCommits(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addValue(new ValueNo(0L), "value");
            db.replaceDocument(DocId.of(2), doc);
            db.commit();
            db.replaceDocument(DocId.of(1), doc);
            db.replaceDocument(DocId.of(3), doc);
            final String pending = db.document(DocId.of(3)).value(new ValueNo(0L))
                .orElseThrow();
            db.commit();
            Assertions.assertEquals(
                List.of("value", "value", new TermCount(3)),
                List.of(
                    pending,
                    db.document(DocId.of(3)).value(new ValueNo(0L)).orElseThrow(),
                    db.valueFrequency(new ValueNo(0L))
                ),
                "Reference valuesaftercommit1"
            );
        }
    }

    @Test
    void deletesAndRejectsMissingDocuments(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.addDocument(VidrioWritesTest.plain("one"));
            db.addDocument(VidrioWritesTest.plain("two"));
            db.deleteDocument(DocId.of(1));
            Assertions.assertThrows(
                DocNotFoundError.class, () -> db.document(DocId.of(1)),
                "Deleted"
            );
            Assertions.assertThrows(
                DocNotFoundError.class, () -> db.deleteDocument(DocId.of(1)),
                "Deleted twice"
            );
            Assertions.assertThrows(
                DocNotFoundError.class, () -> db.deleteDocument(DocId.of(9)),
                "Never added"
            );
            db.commit();
            Assertions.assertThrows(
                DocNotFoundError.class, () -> db.deleteDocument(DocId.of(1)),
                "Deleted and committed"
            );
            Assertions.assertEquals(
                List.of(new TermCount(1), DocId.of(2), false, DocId.of(3)),
                List.of(
                    db.docCount(), db.lastDocId(), db.termExists("one"),
                    db.addDocument(VidrioWritesTest.plain("three"))
                ),
                "Identifier not reused"
            );
        }
    }

    @Test
    void deletesAndReplacesByTerm(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.addDocument(VidrioWritesTest.plain("same"));
            db.addDocument(VidrioWritesTest.plain("same"));
            db.addDocument(VidrioWritesTest.plain("other"));
            final DocId replaced = db.replaceDocument(
                "same", VidrioWritesTest.plain("fresh")
            );
            db.deleteDocument("other");
            db.commit();
            Assertions.assertEquals(
                List.of(DocId.of(1), new TermCount(1), false, false, "fresh"),
                List.of(
                    replaced, db.docCount(), db.termExists("same"),
                    db.termExists("other"), db.document(DocId.of(1)).data()
                ),
                "First document replaced, the rest deleted"
            );
        }
    }

    @Test
    void cancelDropsPendingChanges(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.addDocument(VidrioWritesTest.plain("kept"));
            db.commit();
            final DocumentInternal doc = VidrioWritesTest.plain("befuddlement");
            doc.addValue(VidrioWritesTest.SLOT, "answer");
            db.addDocument(doc);
            db.setMetadata("key", "value");
            final List<Object> pending = List.of(
                db.docCount(), db.termFrequency("befuddlement"),
                db.valueFrequency(VidrioWritesTest.SLOT),
                db.valueLowerBound(VidrioWritesTest.SLOT)
            );
            db.cancel();
            Assertions.assertEquals(
                List.of(
                    new TermCount(2), new TermCount(1), new TermCount(1), "answer",
                    new TermCount(1), TermCount.ZERO, TermCount.ZERO, "", "",
                    DocId.of(2)
                ),
                List.of(
                    pending.get(0), pending.get(1), pending.get(2), pending.get(3),
                    db.docCount(), db.termFrequency("befuddlement"),
                    db.valueFrequency(VidrioWritesTest.SLOT),
                    db.valueUpperBound(VidrioWritesTest.SLOT), db.metadata("key"),
                    db.addDocument(VidrioWritesTest.plain("later"))
                ),
                "Reference canceltransaction1, identifier counter restored"
            );
        }
    }

    @Test
    void transactionsCommitAndCancel(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.beginTransaction(true);
            db.addDocument(VidrioWritesTest.plain("committed"));
            Assertions.assertThrows(
                InvalidOperationError.class, () -> db.beginTransaction(false),
                "Already in a transaction"
            );
            Assertions.assertThrows(
                InvalidOperationError.class, db::commit,
                "No commit during a transaction"
            );
            db.commitTransaction();
            db.beginTransaction(false);
            db.addDocument(VidrioWritesTest.plain("dropped"));
            final TermCount inside = db.docCount();
            db.cancelTransaction();
            Assertions.assertEquals(
                List.of(new TermCount(2), new TermCount(1), true, false),
                List.of(
                    inside, db.docCount(), db.termExists("committed"),
                    db.termExists("dropped")
                ),
                "Reference committransaction1 and canceltransaction1"
            );
        }
    }

    @Test
    void transactionBeginCommitsPendingChanges(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final DocId pending = db.addDocument(
                VidrioWritesTest.plain("pending_update")
            );
            db.beginTransaction(true);
            db.addDocument(VidrioWritesTest.plain("befuddlement"));
            final TermCount inside = db.docCount();
            db.cancelTransaction();
            Assertions.assertEquals(
                List.of(new TermCount(2), new TermCount(1), true, "pending_update"),
                List.of(
                    inside, db.docCount(), db.termExists("pending_update"),
                    db.document(pending).data()
                ),
                "Reference canceltransaction2"
            );
        }
    }

    @Test
    void unflushedTransactionKeepsEarlierChangesOnCancel(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.addDocument(VidrioWritesTest.plain("before"));
            db.beginTransaction(false);
            db.addDocument(VidrioWritesTest.plain("inside"));
            db.cancelTransaction();
            db.addDocument(VidrioWritesTest.plain("after"));
            db.commit();
            Assertions.assertEquals(
                List.of(new TermCount(1), false, true),
                List.of(
                    db.docCount(), db.termExists("before"), db.termExists("after")
                ),
                "Cancel of an unflushed transaction drops everything pending"
            );
        }
    }

    @Test
    void transactionMisuseIsRejected(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            Assertions.assertThrows(
                InvalidOperationError.class, () -> db.commitTransaction(),
                "Commit outside"
            );
            Assertions.assertThrows(
                InvalidOperationError.class, () -> db.cancelTransaction(),
                "Cancel outside"
            );
            db.beginTransaction(true);
            db.commitTransaction();
            db.beginTransaction(false);
            db.cancelTransaction();
            Assertions.assertThrows(
                InvalidOperationError.class, () -> db.commitTransaction(),
                "Reference badtransaction1"
            );
        }
    }

    @Test
    void closeDuringTransactionDropsChanges(@TempDir final Path dir) {
        final List<TermCount> counts = new ArrayList<>(4);
        for (final boolean flushed : new boolean[] {true, false}) {
            final Path path = dir.resolve(String.valueOf(flushed));
            try (WritableDatabase db = VidrioWritesTest.fresh(path)) {
                db.beginTransaction(flushed);
                db.addDocument(new DocumentInternal());
                counts.add(db.docCount());
            }
            try (Database db = DatabaseFactory.open(path)) {
                counts.add(db.docCount());
            }
        }
        Assertions.assertEquals(
            List.of(new TermCount(1), TermCount.ZERO, new TermCount(1), TermCount.ZERO),
            counts, "Reference closedb5"
        );
    }

    @Test
    void closeCommitsOutsideTransactions(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.addDocument(new DocumentInternal());
        }
        try (Database db = DatabaseFactory.open(dir)) {
            Assertions.assertEquals(
                new TermCount(1), db.docCount(), "Reference closedb4"
            );
        }
    }

    @Test
    void closedDatabaseRejectsChanges(@TempDir final Path dir) {
        final WritableDatabase base = VidrioWritesTest.fresh(dir);
        base.addDocument(new DocumentInternal());
        base.setMetadata("foo", "FOO");
        base.close();
        base.close();
        final DocumentInternal doc = new DocumentInternal();
        Assertions.assertAll(
            "Reference closedb7 and closedb10",
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.addDocument(doc), "add"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.deleteDocument(DocId.of(1)),
                "delete"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class,
                () -> base.replaceDocument(DocId.of(2), doc), "replace"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.replaceDocument("Qi", doc),
                "replace by term"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.setMetadata("test", "TEST"),
                "set metadata"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.metadata("foo"),
                "get metadata"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.metadataKeys(""), "keys"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, base::cancel, "cancel"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, base::commit, "commit"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.postings("x"), "postings"
            )
        );
    }

    @Test
    void closedReaderRejectsReads(@TempDir final Path dir) {
        VidrioWritesTest.fresh(dir).close();
        final Database base = DatabaseFactory.open(dir);
        base.close();
        base.close();
        Assertions.assertAll(
            "Reference closedb1",
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, base::docCount, "count"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.allTerms(""), "terms"
            ),
            () -> Assertions.assertThrows(
                DatabaseClosedError.class, () -> base.document(DocId.of(1)),
                "document"
            )
        );
    }

    @Test
    void rejectsOverlongTerms(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final List<String> messages = new ArrayList<>(5);
            for (int len = 246; len <= 290; len += 11) {
                final DocumentInternal doc = VidrioWritesTest.plain("X".repeat(len));
                try {
                    db.addDocument(doc);
                } catch (final InvalidArgumentError ex) {
                    messages.add(ex.getMessage().replaceAll("X+", "X"));
                }
            }
            for (int len = 240; len <= 245; ++len) {
                db.addDocument(VidrioWritesTest.plain("X".repeat(len)));
            }
            db.commit();
            db.addDocument(VidrioWritesTest.plain("ok"));
            db.commit();
            Assertions.assertEquals(
                List.of(
                    new TermCount(7), true, false, TermCount.ZERO,
                    Collections.nCopies(5, "Term too long (> 245): X")
                ),
                List.of(
                    db.docCount(), db.termExists("X".repeat(245)),
                    db.termExists("X".repeat(246)),
                    db.termFrequency("X".repeat(300)), messages
                ),
                "Reference termtoolong1; long terms unknown on read"
            );
        }
    }

    @Test
    void rejectsPositionZero(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final DocumentInternal doc = new DocumentInternal();
            doc.addPosting("zero", new TermPos(0), new TermCount(1));
            doc.addPosting("zero", new TermPos(1), new TermCount(1));
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> db.addDocument(doc),
                "Position 0 cannot be stored"
            );
            Assertions.assertEquals(
                TermCount.ZERO, db.docCount(), "Failed change dropped"
            );
        }
    }

    @Test
    void runsOutOfIdentifiers(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            final DocumentInternal doc = VidrioWritesTest.plain("word");
            db.replaceDocument(DocId.of(Integer.MAX_VALUE), doc);
            Assertions.assertThrows(
                RangeError.class, () -> db.addDocument(doc),
                "Reference nomoredocids1"
            );
            Assertions.assertThrows(
                RangeError.class, () -> db.replaceDocument("Q42", doc),
                "Replace by absent term adds"
            );
        }
    }

    @Test
    void secondWriterIsLockedOut(@TempDir final Path dir) {
        final WritableDatabase first = VidrioWritesTest.fresh(dir);
        Assertions.assertThrows(
            DatabaseLockError.class,
            () -> DatabaseFactory.openWritable(dir, DatabaseAction.OPEN.code()),
            "Reference closedb2"
        );
        first.close();
        try (
            WritableDatabase second = DatabaseFactory.openWritable(
                dir, DatabaseAction.OPEN.code()
            )
        ) {
            Assertions.assertThrows(
                DatabaseClosedError.class, () -> first.postings("paragraph"),
                "First closed"
            );
            Assertions.assertFalse(
                second.postings("paragraph").hasNext(), "Second open"
            );
        }
    }

    @Test
    void metadataRoundTripsAndLists(@TempDir final Path dir) {
        try (WritableDatabase db = VidrioWritesTest.fresh(dir)) {
            db.setMetadata("foo", "FOO");
            db.setMetadata("bar", "BAR");
            db.setMetadata("baz", "BAZ");
            db.setMetadata("baz", "");
            db.commit();
        }
        try (Database db = DatabaseFactory.open(dir)) {
            Assertions.assertEquals(
                List.of("FOO", "BAR", "", List.of("bar", "foo")),
                List.of(
                    db.metadata("foo"), db.metadata("bar"), db.metadata("baz"),
                    VidrioWritesTest.keys(db)
                ),
                "Metadata committed"
            );
        }
    }

    private static List<String> keys(final Database base) {
        final List<String> result = new ArrayList<>(0);
        try (TermIterator iter = base.metadataKeys("")) {
            while (iter.hasNext()) {
                result.add(iter.next());
            }
        }
        return result;
    }

    private static String term(
        final Database base, final DocId doc, final String term
    ) {
        final List<String> positions = new ArrayList<>(0);
        try (PositionIterator iter = base.positions(doc, term)) {
            while (iter.hasNext()) {
                positions.add(iter.next().toString());
            }
        }
        return String.format(
            "%s:%s:%s", term, base.positionCount(doc, term), positions
        );
    }

    private static WritableDatabase fresh(final Path dir) {
        return DatabaseFactory.openWritable(dir, DatabaseAction.CREATE.code());
    }

    private static DocumentInternal plain(final String term) {
        final DocumentInternal doc = new DocumentInternal();
        doc.setData(term);
        doc.addTerm(term, new TermCount(1));
        return doc;
    }
}
