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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioTermList}: documents written through the writer
 * and read back against the model, moves and misuse.
 *
 * @since 0.1.0
 */
final class VidrioTermListTest {

    /**
     * Terms a, b, d and f with frequencies 1 to 4, in hexadecimal.
     */
    private static final String FOUR = "0a040161010601620801640a0166";

    @Test
    void readsModelDocuments() {
        final Documents docs = Documents.of(1L);
        docs.add(1, Map.of());
        docs.add(2, Map.of("alone", new TermCount(1)));
        docs.add(3, VidrioTermListTest.hundreds());
        docs.add(4, VidrioTermListTest.longOnes());
        docs.add(5, VidrioTermListTest.wide());
        docs.add(6, Map.of("alone", new TermCount(5), "t007", TermCount.ZERO));
        final List<String> faults = new ArrayList<>(0);
        docs.commit();
        for (final DocId doc : docs.docs()) {
            final VidrioTermList list = docs.open(doc);
            final List<Object> expected = List.of(
                docs.expected(doc), new TermCount(docs.terms(doc).size()),
                docs.length(doc),
                new TermCount(
                    Math.min(docs.terms(doc).size(), docs.length(doc).value())
                )
            );
            final List<Object> seen = List.of(
                Documents.read(list), list.approxSize(), list.docLength(),
                list.uniqueTerms()
            );
            if (!expected.equals(seen)) {
                faults.add(String.format("%s: %s", doc, seen));
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Terms, frequencies and lengths as the model"
        );
    }

    @Test
    void keepsByteOrderOfTerms() {
        final Documents docs = Documents.of(2L);
        docs.add(1, VidrioTermListTest.wide());
        docs.commit();
        final List<String> seen = new ArrayList<>(5);
        final VidrioTermList list = docs.open(DocId.of(1));
        list.next();
        while (!list.atEnd()) {
            seen.add(list.term());
            list.next();
        }
        final List<String> expected = new ArrayList<>(5);
        for (final String term : new TreeMap<>(VidrioTermListTest.wide()).keySet()) {
            expected.add(term);
        }
        Assertions.assertEquals(
            List.of(
                List.of(
                    "z", "über", "日本",
                    Character.toString(0xFFFF), Character.toString(0x1F600)
                ),
                false
            ),
            List.of(seen, expected.equals(seen)),
            "Code point order, not the order of the string type"
        );
    }

    @Test
    void skipsForwardOnly() {
        final List<Object> seen = new ArrayList<>(8);
        final VidrioTermList list = VidrioTermListTest.four();
        list.skipTo("c");
        seen.add(list.term());
        list.skipTo("a");
        seen.add(list.term());
        list.skipTo("d");
        seen.add(list.term());
        seen.add(list.wdf());
        seen.add(list.skipTo("e").term());
        list.skipTo("g");
        seen.add(list.atEnd());
        seen.add(VidrioTermListTest.four().skipTo("").term());
        seen.add(VidrioTermListTest.four().skipTo("b").wdf());
        Assertions.assertEquals(
            List.of(
                "d", "d", "d", new TermCount(3), "f", true, "a", new TermCount(2)
            ),
            seen,
            "First term at or after the target, never backwards"
        );
    }

    @Test
    void refusesTermBeforeStartAndAfterEnd() {
        final List<Object> seen = new ArrayList<>(10);
        final VidrioTermList list = VidrioTermListTest.four();
        seen.add(VidrioTermListTest.failure(list::term));
        seen.add(VidrioTermListTest.failure(list::wdf));
        seen.add(VidrioTermListTest.failure(list::termFrequency));
        seen.add(VidrioTermListTest.failure(list::positionCount));
        seen.add(VidrioTermListTest.failure(list::positionList));
        seen.add(list.atEnd());
        list.next();
        seen.add(list.positionCount());
        seen.add(list.positionList().next());
        seen.add(list.heldPositions().isPresent());
        for (int idx = 0; idx < 4; ++idx) {
            list.next();
        }
        seen.add(list.atEnd());
        seen.add(VidrioTermListTest.failure(list::term));
        seen.add(VidrioTermListTest.failure(list.next()::wdf));
        Assertions.assertEquals(
            List.of(
                InvalidOperationError.class, InvalidOperationError.class,
                InvalidOperationError.class, InvalidOperationError.class,
                InvalidOperationError.class, false, TermCount.ZERO, false,
                false, true, InvalidOperationError.class,
                InvalidOperationError.class
            ),
            seen,
            "No term before the first move and after the end, no positions"
        );
    }

    @Test
    void boundsUniqueTermsByLength() {
        final VidrioTermList list = VidrioTermListTest.list("0102016101020162");
        Assertions.assertEquals(
            List.of(new TermCount(2), new TermCount(1), new TermCount(1)),
            List.of(list.approxSize(), list.docLength(), list.uniqueTerms()),
            "Two terms, length one"
        );
    }

    @Test
    void looksUpTermFrequencyLazily() {
        final List<Object> seen = new ArrayList<>(4);
        final VidrioTable postings = Readback.crafted(
            "61", "0206003102020104", "64", "010300310003"
        );
        try (postings) {
            final VidrioTermList list = VidrioTermList.over(
                HexFormat.of().parseHex(VidrioTermListTest.FOUR), DocId.of(1),
                TermListTable.of(
                    Readback.crafted(), PostingTable.of(postings),
                    Positions.none()
                )
            );
            list.next();
            seen.add(list.termFrequency());
            seen.add(list.termFrequency());
            list.next();
            seen.add(list.termFrequency());
            list.next();
            seen.add(list.termFrequency());
        }
        Assertions.assertEquals(
            List.of(
                new TermCount(2), new TermCount(2), TermCount.ZERO,
                new TermCount(1)
            ),
            seen,
            "Frequency from the posting table, 0 for an absent term"
        );
    }

    private static VidrioTermList four() {
        return VidrioTermListTest.list(VidrioTermListTest.FOUR);
    }

    private static VidrioTermList list(final String hex) {
        return VidrioTermList.over(
            HexFormat.of().parseHex(hex), DocId.of(1),
            TermListTable.of(
                Readback.crafted(), PostingTable.of(Readback.crafted()),
                Positions.none()
            )
        );
    }

    private static Map<String, TermCount> hundreds() {
        final Map<String, TermCount> terms = new TreeMap<>(TermOrder.INSTANCE);
        for (int idx = 0; idx < 300; ++idx) {
            int wdf = idx * 7 % 131;
            if (idx > 290) {
                wdf = 100_000 + idx;
            }
            terms.put(String.format("t%03d", idx), new TermCount(wdf));
        }
        return terms;
    }

    private static Map<String, TermCount> longOnes() {
        final Map<String, TermCount> terms = new TreeMap<>(TermOrder.INSTANCE);
        for (int idx = 0; idx < 20; ++idx) {
            terms.put(
                String.format("%s%03d", "p".repeat(200), idx),
                new TermCount(idx)
            );
        }
        terms.put("q".repeat(240), new TermCount(1));
        return terms;
    }

    private static Map<String, TermCount> wide() {
        return Map.of(
            "über", new TermCount(1), "日本", new TermCount(2),
            Character.toString(0xFFFF), new TermCount(3),
            Character.toString(0x1F600), new TermCount(4), "z", new TermCount(5)
        );
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
