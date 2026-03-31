/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioAllTermsList}: a corpus with hundreds of terms
 * under several prefixes and multi-chunk lists, skips, misuse and the
 * keys that are not terms.
 *
 * @since 0.1.0
 */
final class VidrioAllTermsListTest {

    /**
     * Term of a single zero byte.
     */
    private static final String ZERO = Character.toString(0);

    /**
     * Prefix of a hundred and twenty terms.
     */
    private static final String BETA = "beta";

    /**
     * Keys and tags of a table whose keys are not all terms.
     */
    private static final String[] MIXED = {
        "00e0", "0000003104030302",
        "00ff", "010300310003",
        "6100", "010300310003",
        "6100ff62", "0206003102020104",
        "6100ff62000005", "3101070001",
        "62", "010300310003",
        "63000005", "3101070001",
    };

    @Test
    void listsEveryTermOfCorpus() {
        final Corpus corpus = VidrioAllTermsListTest.corpus();
        final List<Object> seen = new ArrayList<>(3);
        final TableState state = corpus.commit(1L);
        try (VidrioTable table = corpus.table(state)) {
            final VidrioAllTermsList list =
                VidrioAllTermsList.open(table, state.entries(), "");
            seen.add(list.approxSize());
            seen.add(VidrioAllTermsListTest.read(list));
            seen.add(Readback.chunks(table, Corpus.EVERY).size() > 0);
        }
        Assertions.assertEquals(
            List.of(
                new TermCount((int) state.entries().value()),
                VidrioAllTermsListTest.expected(corpus, ""), true
            ),
            seen,
            "Every term once with its frequency, continuation chunks skipped"
        );
    }

    @Test
    void restrictsToPrefix() {
        final Corpus corpus = VidrioAllTermsListTest.corpus();
        final List<Object> seen = new ArrayList<>(5);
        final List<Object> expected = new ArrayList<>(5);
        final TableState state = corpus.commit(1L);
        try (VidrioTable table = corpus.table(state)) {
            for (final String prefix
                : List.of(
                    VidrioAllTermsListTest.BETA, "beta007", "gamma1", "zzz",
                    VidrioAllTermsListTest.ZERO, "nul"
                )) {
                seen.add(
                    VidrioAllTermsListTest.read(
                        VidrioAllTermsList.open(table, state.entries(), prefix)
                    )
                );
                expected.add(VidrioAllTermsListTest.expected(corpus, prefix));
            }
        }
        Assertions.assertEquals(
            expected, seen, "Terms with the prefix only, exact prefix included"
        );
    }

    @Test
    void skipsForwardOnly() {
        final Corpus corpus = VidrioAllTermsListTest.corpus();
        final List<Object> seen = new ArrayList<>(12);
        final TableState state = corpus.commit(1L);
        try (VidrioTable table = corpus.table(state)) {
            final VidrioAllTermsList list =
                VidrioAllTermsList.open(table, state.entries(), "");
            seen.add(list.skipTo("beta050").term());
            seen.add(list.termFrequency().value() > 0);
            seen.add(list.skipTo("beta0505").term());
            seen.add(list.skipTo("alpha000").term());
            seen.add(list.next().term());
            seen.add(list.skipTo("zzzz").atEnd());
            seen.add(list.skipTo("a").atEnd());
            seen.add(list.next().atEnd());
            seen.add(
                VidrioAllTermsList.open(table, state.entries(), "")
                    .skipTo("").term()
            );
            final VidrioAllTermsList beta = VidrioAllTermsList.open(
                table, state.entries(), VidrioAllTermsListTest.BETA
            );
            seen.add(beta.skipTo(VidrioAllTermsListTest.BETA).term());
            seen.add(beta.skipTo("gamma").atEnd());
            seen.add(
                VidrioAllTermsList.open(
                    table, state.entries(), VidrioAllTermsListTest.BETA
                ).skipTo("every").atEnd()
            );
        }
        Assertions.assertEquals(
            List.of(
                "beta050", true, "beta051", "beta051", "beta052", true, true,
                true, VidrioAllTermsListTest.ZERO, "beta000", true, true
            ),
            seen,
            "First term at or after the target, never backwards"
        );
    }

    @Test
    void refusesTermBeforeStartAndAfterEnd() {
        final List<Object> seen = new ArrayList<>(10);
        final VidrioTable table = Readback.crafted(VidrioAllTermsListTest.MIXED);
        try (table) {
            final VidrioAllTermsList list =
                VidrioAllTermsList.open(table, new EntryCount(7L), "");
            seen.add(VidrioAllTermsListTest.failure(list::term));
            seen.add(VidrioAllTermsListTest.failure(list::termFrequency));
            seen.add(VidrioAllTermsListTest.failure(list::wdf));
            seen.add(VidrioAllTermsListTest.failure(list::positionCount));
            seen.add(VidrioAllTermsListTest.failure(list::positionList));
            seen.add(list.heldPositions().isPresent());
            seen.add(list.atEnd());
            for (int idx = 0; idx < 4; ++idx) {
                list.next();
            }
            seen.add(list.atEnd());
            seen.add(VidrioAllTermsListTest.failure(list::term));
            seen.add(VidrioAllTermsListTest.failure(list.next()::termFrequency));
        }
        Assertions.assertEquals(
            List.of(
                InvalidOperationError.class, InvalidOperationError.class,
                InvalidOperationError.class, InvalidOperationError.class,
                InvalidOperationError.class, false, false, true,
                InvalidOperationError.class, InvalidOperationError.class
            ),
            seen,
            "No term before the first move and after the end"
        );
    }

    @Test
    void skipsKeysThatAreNotTerms() {
        final List<Object> seen = new ArrayList<>(10);
        final VidrioTable table = Readback.crafted(VidrioAllTermsListTest.MIXED);
        try (table) {
            seen.add(
                VidrioAllTermsListTest.read(
                    VidrioAllTermsList.open(table, new EntryCount(7L), "")
                )
            );
            final VidrioAllTermsList list =
                VidrioAllTermsList.open(table, new EntryCount(7L), "");
            seen.add(list.skipTo("a").term());
            seen.add(list.termFrequency());
            seen.add(list.next().term());
            final VidrioAllTermsList tail =
                VidrioAllTermsList.open(table, new EntryCount(7L), "");
            seen.add(tail.skipTo("c").term());
            seen.add(tail.termFrequency());
            seen.add(tail.next().atEnd());
            seen.add(
                VidrioAllTermsList.open(table, new EntryCount(7L), "a")
                    .skipTo(String.format("a%sc", VidrioAllTermsListTest.ZERO))
                    .atEnd()
            );
            seen.add(
                VidrioAllTermsList.open(table, new EntryCount(7L), "")
                    .skipTo(String.format("a%sb", VidrioAllTermsListTest.ZERO))
                    .next().term()
            );
            seen.add(
                VidrioAllTermsList.open(table, new EntryCount(7L), "b")
                    .next().termFrequency()
            );
        }
        Assertions.assertEquals(
            List.of(
                List.of(
                    String.format("%s:1", VidrioAllTermsListTest.ZERO),
                    String.format("a%sb:2", VidrioAllTermsListTest.ZERO), "b:1"
                ),
                "a", new TermCount(1),
                String.format("a%sb", VidrioAllTermsListTest.ZERO), "c",
                new TermCount(49), true, true, "b", new TermCount(1)
            ),
            seen,
            "Lengths, a key ending in a zero byte and continuations skipped"
        );
    }

    @Test
    void endsAtOnceWithoutTerms() {
        final List<Object> seen = new ArrayList<>(4);
        final VidrioTable lengths =
            Readback.crafted("00e0", "0000003104030302");
        try (lengths) {
            seen.add(
                VidrioAllTermsList.open(lengths, new EntryCount(1L), "")
                    .next().atEnd()
            );
            seen.add(
                VidrioAllTermsList.open(lengths, new EntryCount(1L), "x")
                    .next().atEnd()
            );
            seen.add(
                VidrioAllTermsList.open(lengths, new EntryCount(1L), "")
                    .skipTo("a").atEnd()
            );
        }
        try (VidrioTable empty = Readback.crafted()) {
            seen.add(
                VidrioAllTermsList.open(empty, EntryCount.EMPTY, "")
                    .next().atEnd()
            );
        }
        Assertions.assertEquals(
            List.of(true, true, true, true), seen,
            "Only the document lengths, or nothing at all"
        );
    }

    @Test
    void keepsTermFrequencyUntilMove() {
        final List<Object> seen = new ArrayList<>(3);
        final VidrioTable table = Readback.crafted(VidrioAllTermsListTest.MIXED);
        try (table) {
            final VidrioAllTermsList list =
                VidrioAllTermsList.open(table, new EntryCount(7L), "");
            list.next();
            list.next();
            seen.add(list.termFrequency());
            seen.add(list.termFrequency());
            seen.add(list.next().termFrequency());
        }
        Assertions.assertEquals(
            List.of(new TermCount(2), new TermCount(2), new TermCount(1)),
            seen,
            "Frequency from the first chunk, read once per term"
        );
    }

    private static Corpus corpus() {
        final Corpus corpus = Corpus.of(5L, BlockSize.MIN, 1);
        final Random random = new Random(5L);
        for (int doc = 1; doc <= 1500; ++doc) {
            final Map<String, TermCount> terms = new TreeMap<>();
            terms.put(Corpus.EVERY, new TermCount(1));
            terms.put(
                String.format("nul%sx", VidrioAllTermsListTest.ZERO),
                new TermCount(1)
            );
            terms.put(
                String.format("alpha%03d", random.nextInt(100)),
                new TermCount(1 + random.nextInt(3))
            );
            terms.put(
                String.format("beta%03d", random.nextInt(120)), new TermCount(1)
            );
            terms.put(
                String.format("gamma%03d", random.nextInt(120)), new TermCount(2)
            );
            if (doc % 500 == 0) {
                terms.put(VidrioAllTermsListTest.ZERO, new TermCount(1));
            }
            corpus.add(DocId.of(doc), terms);
        }
        return corpus;
    }

    private static List<String> expected(
        final Corpus corpus, final String prefix
    ) {
        final Map<String, Integer> counts = new TreeMap<>(TermOrder.INSTANCE);
        for (final DocId doc : corpus.docs()) {
            for (final String term : corpus.terms(doc).keySet()) {
                if (term.startsWith(prefix)) {
                    counts.merge(term, 1, Integer::sum);
                }
            }
        }
        final List<String> result = new ArrayList<>(counts.size());
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            result.add(
                String.format("%s:%d", entry.getKey(), entry.getValue())
            );
        }
        return result;
    }

    private static List<String> read(final VidrioAllTermsList list) {
        final List<String> result = new ArrayList<>(0);
        list.next();
        while (!list.atEnd()) {
            result.add(
                String.format("%s:%s", list.term(), list.termFrequency())
            );
            list.next();
        }
        return result;
    }

    private static Class<?> failure(final Runnable action) {
        Class<?> result = Void.class;
        try {
            action.run();
        } catch (final InvalidOperationError ex) {
            result = ex.getClass();
        }
        return result;
    }
}
