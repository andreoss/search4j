/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DatabaseLockError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the defaults of {@link Shard} and the interfaces it joins.
 *
 * <p>The fixture holds term "x" in documents 1 and 3 and term "y" in
 * document 2.</p>
 *
 * @since 0.1.0
 */
final class ShardDefaultsTest {

    @Test
    void refusesDictionaryWrites() {
        final Shard shard = new InMemoryShard();
        Assertions.assertEquals(
            5,
            ShardDefaultsTest.unimplemented(
                List.of(
                    () -> shard.addSpelling("a", new TermCount(1)),
                    () -> shard.removeSpelling("a", new TermCount(1)),
                    () -> shard.addSynonym("a", "b"),
                    () -> shard.removeSynonym("a", "b"),
                    () -> shard.clearSynonyms("a")
                )
            ),
            "Every dictionary write"
        );
    }

    @Test
    void findsNoDictionaryEntries() {
        final Shard shard = new InMemoryShard();
        Assertions.assertEquals(
            List.of(true, true, TermCount.ZERO, true, true),
            List.of(
                shard.openSpellingTermList("a").atEnd(),
                shard.openSpellingWordList().atEnd(),
                shard.spellingFrequency("a"),
                shard.openSynonyms("a").atEnd(),
                shard.openSynonymKeys("").atEnd()
            ),
            "Empty dictionaries"
        );
    }

    @Test
    void refusesRevisionAndRange() {
        final Shard shard = new InMemoryShard();
        Assertions.assertEquals(
            2,
            ShardDefaultsTest.unimplemented(
                List.of(shard::revision, shard::usedDocIds)
            ),
            "Revision and range"
        );
    }

    @Test
    void hasNoIdentityOrLock() {
        final Shard shard = new InMemoryShard();
        Assertions.assertEquals(
            List.of(true, false, false),
            List.of(shard.uuid().isEmpty(), shard.locked(), shard.reopen()),
            "No identifier, not locked, unchanged"
        );
    }

    @Test
    void relocksForReading() {
        final Shard shard = new InMemoryShard();
        Assertions.assertSame(shard, shard.relock(false), "Same shard");
    }

    @Test
    void refusesRelockForWriting() {
        Assertions.assertThrows(
            DatabaseLockError.class,
            () -> new InMemoryShard().relock(true),
            "Writable"
        );
    }

    @Test
    void boundsUniqueTermsByLength() {
        final Shard shard = new InMemoryShard();
        shard.addDocument(ShardDefaultsTest.doc("a", "b"));
        Assertions.assertEquals(
            List.of(new TermCount(1), shard.docLengthUpperBound()),
            List.of(
                shard.uniqueTermsLowerBound(), shard.uniqueTermsUpperBound()
            ),
            "Lower one, upper the length bound"
        );
    }

    @Test
    void boundsUniqueTermsOfTermlessShard() {
        final Shard shard = new InMemoryShard();
        shard.addDocument(new DocumentInternal());
        Assertions.assertEquals(
            TermCount.ZERO, shard.uniqueTermsLowerBound(), "No term"
        );
    }

    @Test
    void readsValuesSlowlyByDefault() {
        final ValueList list = ShardDefaultsTest.wrap(
            ShardDefaultsTest.filled(), TransactionState.UNIMPLEMENTED, true
        ).openValueList(new ValueNo(1L));
        list.next();
        Assertions.assertEquals(
            "SlowValueList(slot=1, docid=1, value=\"x\")",
            list.description(),
            "Slow list on first holder"
        );
    }

    @Test
    void deletesEveryDocumentOfTerm() {
        final Shard shard = ShardDefaultsTest.writable();
        shard.deleteDocument("x");
        Assertions.assertEquals(
            List.of(new TermCount(1), false, true),
            List.of(
                shard.docCount(), shard.termExists("x"), shard.termExists("y")
            ),
            "Only the other term left"
        );
    }

    @Test
    void deletesNothingForUnknownTerm() {
        final Shard shard = ShardDefaultsTest.writable();
        shard.deleteDocument("absent");
        Assertions.assertEquals(new TermCount(3), shard.docCount(), "All kept");
    }

    @Test
    void addsWhenReplacingUnknownTerm() {
        final Shard shard = ShardDefaultsTest.writable();
        Assertions.assertEquals(
            List.of(new DocId(4), new TermCount(4)),
            List.of(
                shard.replaceDocument("absent", ShardDefaultsTest.doc("z")),
                shard.docCount()
            ),
            "Added"
        );
    }

    @Test
    void replacesFirstAndDeletesRest() {
        final Shard shard = ShardDefaultsTest.writable();
        Assertions.assertEquals(
            List.of(new DocId(1), new TermCount(2), false, true, true),
            List.of(
                shard.replaceDocument("x", ShardDefaultsTest.doc("z")),
                shard.docCount(),
                shard.termExists("x"),
                shard.termExists("z"),
                shard.termExists("y")
            ),
            "First replaced, rest deleted"
        );
    }

    @Test
    void refusesTermWritesWhenReadOnly() {
        final Shard shard = ShardDefaultsTest.wrap(
            ShardDefaultsTest.filled(), TransactionState.READ_ONLY, false
        );
        int refused = 0;
        for (final Runnable call : List.<Runnable>of(
            () -> shard.deleteDocument("x"),
            () -> shard.replaceDocument("x", new DocumentInternal())
        )) {
            try {
                call.run();
            } catch (final InvalidOperationError ex) {
                refused += 1;
            }
        }
        Assertions.assertEquals(
            List.of(2, new TermCount(3)),
            List.of(refused, shard.docCount()),
            "Both refused, nothing changed"
        );
    }

    @Test
    void runsTransactionsThroughStateMachine() {
        final Shard shard = ShardDefaultsTest.wrap(
            ShardDefaultsTest.filled(), TransactionState.NONE, false
        );
        shard.beginTransaction(true);
        final TransactionState open = shard.transactions().state();
        shard.endTransaction(false);
        Assertions.assertEquals(
            List.of(TransactionState.FLUSHED, TransactionState.NONE),
            List.of(open, shard.transactions().state()),
            "Opened then closed"
        );
    }

    @Test
    void rejectsTransactionsWithoutSupport() {
        final Shard shard = new InMemoryShard();
        Assertions.assertEquals(
            2,
            ShardDefaultsTest.unimplemented(
                List.of(
                    () -> shard.beginTransaction(false),
                    () -> shard.endTransaction(true)
                )
            ),
            "Begin and end"
        );
    }

    private static int unimplemented(final List<Runnable> calls) {
        int count = 0;
        for (final Runnable call : calls) {
            try {
                call.run();
            } catch (final UnimplementedError ex) {
                count += 1;
            }
        }
        return count;
    }

    private static Shard writable() {
        return ShardDefaultsTest.wrap(
            ShardDefaultsTest.filled(), TransactionState.UNIMPLEMENTED, false
        );
    }

    private static Shard wrap(
        final InMemoryShard origin,
        final TransactionState state,
        final boolean slow
    ) {
        final Transactions states = new Transactions(state);
        final Map<String, Wrapped.Answer> answers;
        if (slow) {
            answers = Map.of(
                "transactions", args -> states,
                "openValueList",
                args -> new SlowValueList(origin, (ValueNo) args[0])
            );
        } else {
            answers = Map.of("transactions", args -> states);
        }
        return Wrapped.of(origin, answers);
    }

    private static InMemoryShard filled() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(ShardDefaultsTest.doc("x"));
        shard.addDocument(ShardDefaultsTest.doc("y"));
        shard.addDocument(ShardDefaultsTest.doc("x"));
        return shard;
    }

    private static DocumentInternal doc(final String... terms) {
        final DocumentInternal doc = new DocumentInternal();
        for (final String term : terms) {
            doc.addTerm(term, new TermCount(1));
        }
        doc.addValue(new ValueNo(1L), terms[0]);
        return doc;
    }
}
