/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests for the term generator.
 */
package io.github.andreoss.search4j.core.termgen;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the term generator.
 *
 * @since 0.1.0
 */
final class TermGeneratorTest {

    @Test
    void indexesWordsAtAscendingPositions() {
        Assertions.assertEquals(
            2, TermGeneratorTest.generated("hello world").termCount().value()
        );
    }

    @Test
    void putsFirstWordAtFirstPosition() {
        Assertions.assertEquals(
            new TermPos(1),
            TermGeneratorTest.positions(
                TermGeneratorTest.generated("hello world"), "hello"
            ).get(0)
        );
    }

    @Test
    void putsSecondWordAtSecondPosition() {
        Assertions.assertEquals(
            new TermPos(2),
            TermGeneratorTest.positions(
                TermGeneratorTest.generated("hello world"), "world"
            ).get(0)
        );
    }

    @Test
    void lowerCasesFirstWord() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.generated("Hello World"), "hello"
            )
        );
    }

    @Test
    void lowerCasesSecondWord() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.generated("Hello World"), "world"
            )
        );
    }

    @Test
    void countsSentenceMarkers() {
        Assertions.assertEquals(
            new TermCount(2),
            TermGeneratorTest.frequency(
                TermGeneratorTest.generated("One. Two."), "xe"
            )
        );
    }

    @Test
    void countsSentenceFieldTerms() {
        Assertions.assertEquals(
            new TermCount(2),
            TermGeneratorTest.frequency(
                TermGeneratorTest.generated("One. Two."), "xsentence"
            )
        );
    }

    @Test
    void appliesPrefix() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.prefixed("t:", "hello"), "t:hello"
            )
        );
    }

    @Test
    void addsFieldTermForUnderscorePrefix() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.prefixed("s_", "hello"), "s"
            )
        );
    }

    @Test
    void addsFieldTermForColonPrefix() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.prefixed("k:", "hello"), "k2..3"
            )
        );
    }

    @Test
    void dropsStopWords() {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.stopper(Stopper.of("the"));
        gen.indexText("the search");
        Assertions.assertEquals(
            TermCount.ZERO, TermGeneratorTest.frequency(doc, "the")
        );
    }

    @Test
    void keepsWordsThatAreNotStopWords() {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.stopper(Stopper.of("the"));
        gen.indexText("the search");
        Assertions.assertEquals(
            TermCount.ONE, TermGeneratorTest.frequency(doc, "search")
        );
    }

    @Test
    void stemsEveryWordWhenStrategyIsAll() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.ALL, "searching"),
                "search"
            )
        );
    }

    @Test
    void keepsRawWordBesideStem() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.ALL, "searching"),
                "searching"
            )
        );
    }

    @Test
    void marksStemWhenStrategyMarksIt() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.ALL_Z, "searching"),
                "searchZ"
            )
        );
    }

    @Test
    void keepsRawWordWhenStrategyMarksTheStem() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.ALL_Z, "searching"),
                "searching"
            )
        );
    }

    @Test
    void stemsSentenceEndWhenStrategyIsSome() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.SOME, "searching."),
                "search"
            )
        );
    }

    @Test
    void keepsLaterWordsWhenStrategyIsSome() {
        Assertions.assertEquals(
            TermCount.ZERO,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.SOME, "other searching"),
                "search"
            )
        );
    }

    @Test
    void stemsUpperCaseWordsWhenStrategyIsSome() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.SOME, "other SEARCHING"),
                "search"
            )
        );
    }

    @Test
    void stemsFlaggedWordWhenStrategyIsSome() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.SOME, "other s_searching"),
                "search"
            )
        );
    }

    @Test
    void keepsWordsWhenStrategyIsNone() {
        Assertions.assertEquals(
            TermCount.ZERO,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(StemStrategy.NONE, "searching"),
                "search"
            )
        );
    }

    @Test
    void stemsUnconditionallyWithFullPositionStrategy() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.stemmed(
                    StemStrategy.SOME_FULL_POS, "searching"
                ),
                "search"
            )
        );
    }

    @Test
    void truncatesLongWords() {
        Assertions.assertEquals(
            TermCount.ONE,
            TermGeneratorTest.frequency(
                TermGeneratorTest.generated("a".repeat(80)),
                "a".repeat(WordBreaker.MAX_WORD)
            )
        );
    }

    @Test
    void indexesTermWithStrategy() {
        final TermGenerator gen = TermGeneratorTest.stemmer();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.indexTerm("Searching", new TermCount(2));
        Assertions.assertEquals(
            new TermCount(2), TermGeneratorTest.frequency(doc, "search")
        );
    }

    @Test
    void indexesTermAtNextPosition() {
        final TermGenerator gen = TermGeneratorTest.stemmer();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.indexTermAtNextPosition("searching", TermCount.ONE);
        Assertions.assertEquals(
            new TermPos(1), TermGeneratorTest.positions(doc, "search").get(0)
        );
    }

    @Test
    void advancesPastIndexedTerm() {
        final TermGenerator gen = TermGeneratorTest.stemmer();
        gen.document(DatabaseFactory.document());
        gen.indexTermAtNextPosition("searching", TermCount.ONE);
        Assertions.assertEquals(2, gen.nextPosition());
    }

    @Test
    void movesNextPosition() {
        final TermGenerator gen = new TermGenerator();
        gen.nextPosition(7);
        Assertions.assertEquals(7, gen.nextPosition());
    }

    @Test
    void rejectsNonPositiveNextPosition() {
        final TermGenerator gen = new TermGenerator();
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> gen.nextPosition(0)
        );
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> gen.nextPosition(0x1_0000_0000L)
        );
    }

    @Test
    void indexesPositionsAboveTheSignedRangeAndWraps() {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.nextPosition(2_147_483_648L);
        gen.indexTermAtNextPosition("high", TermCount.ONE);
        gen.nextPosition(TermPos.MAX.value());
        gen.indexTermAtNextPosition("top", TermCount.ONE);
        Assertions.assertEquals(
            List.of(
                List.of(new TermPos(2_147_483_648L)), List.of(TermPos.MAX), 0L
            ),
            List.of(
                TermGeneratorTest.positions(doc, "high"),
                TermGeneratorTest.positions(doc, "top"), gen.nextPosition()
            ),
            "Positions past 2^31 kept, the next one wrapping to zero"
        );
    }

    @Test
    void recordsDocumentIdentifier() {
        final TermGenerator gen = new TermGenerator();
        gen.lastDocId(12L);
        Assertions.assertEquals(12L, gen.lastDocId());
    }

    @Test
    void startsWithoutDocumentIdentifier() {
        Assertions.assertEquals(-1L, new TermGenerator().lastDocId());
    }

    @Test
    void refusesTextWithoutDocument() {
        final TermGenerator gen = new TermGenerator();
        Assertions.assertThrows(
            InvalidOperationError.class, () -> gen.indexText("x")
        );
    }

    @Test
    void refusesTermWithoutDocument() {
        final TermGenerator gen = new TermGenerator();
        Assertions.assertThrows(
            InvalidOperationError.class,
            () -> gen.indexTerm("x", TermCount.ONE)
        );
    }

    @Test
    void refusesRangeWithoutDocument() {
        final TermGenerator gen = new TermGenerator();
        Assertions.assertThrows(
            InvalidOperationError.class, () -> gen.indexTextRange("1", 0)
        );
    }

    @Test
    void writesSingleNumberToValueSlot() {
        Assertions.assertEquals(
            "12..12", TermGeneratorTest.ranged(-1, "12")
        );
    }

    @Test
    void writesSpanFromRangeStart() {
        Assertions.assertEquals("5..9", TermGeneratorTest.ranged(5, "1 to 9"));
    }

    @Test
    void clearsRangeStartAfterUse() {
        final TermGenerator gen = new TermGenerator();
        gen.document(DatabaseFactory.document());
        gen.rangeStart(5);
        gen.indexTextRange("1 to 9", 0);
        Assertions.assertEquals(-1, gen.rangeStart());
    }

    @Test
    void writesNothingWithoutNumbers() {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.indexTextRange("none", 0);
        Assertions.assertTrue(doc.value(new ValueNo(0)).isEmpty());
    }

    @Test
    void writesEachLooseNumberWithoutAnyRange() {
        Assertions.assertEquals("12..12", TermGeneratorTest.ranged(-1, "x 12 y"));
    }

    @Test
    void readsFieldPrefix() {
        Assertions.assertEquals(
            "s_", TermGenerator.fieldPrefix("s_hello world")
        );
    }

    @Test
    void readsNoFieldPrefix() {
        Assertions.assertEquals("", TermGenerator.fieldPrefix("hello"));
    }

    @Test
    void readsNoFieldPrefixFromEmptyText() {
        Assertions.assertEquals("", TermGenerator.fieldPrefix(""));
    }

    @Test
    void findsLowerNumericBound() {
        Assertions.assertEquals(12, TermGenerator.numericBounds("12 to 42")[0]);
    }

    @Test
    void findsUpperNumericBound() {
        Assertions.assertEquals(42, TermGenerator.numericBounds("12 to 42")[1]);
    }

    @Test
    void findsOneNumberTwice() {
        Assertions.assertEquals(12, TermGenerator.numericBounds("12")[1]);
    }

    @Test
    void findsNoNumericBounds() {
        Assertions.assertEquals(0, TermGenerator.numericBounds("none").length);
    }

    @Test
    void refusesNumberAboveIntegerRange() {
        Assertions.assertEquals(
            0, TermGenerator.numericBounds("2147483648").length
        );
    }

    @Test
    void acceptsLargestInteger() {
        Assertions.assertEquals(
            2, TermGenerator.numericBounds("2147483647").length
        );
    }

    @Test
    void holdsPrefixConfiguration() {
        Assertions.assertEquals("X", TermGeneratorTest.stemmer().prefix());
    }

    @Test
    void holdsStopConfiguration() {
        Assertions.assertEquals(
            "x",
            TermGeneratorTest.stemmer().stopper().words().iterator().next()
        );
    }

    @Test
    void holdsStrategyConfiguration() {
        Assertions.assertEquals(
            StemStrategy.ALL, TermGeneratorTest.stemmer().strategy()
        );
    }

    @Test
    void holdsStemmerConfiguration() {
        Assertions.assertEquals(
            "s", TermGeneratorTest.stemmer().stemmer().name()
        );
    }

    @Test
    void holdsRangeConfiguration() {
        Assertions.assertEquals(4, TermGeneratorTest.stemmer().rangeStart());
    }

    @Test
    void holdsDocumentConfiguration() {
        Assertions.assertNotNull(TermGeneratorTest.stemmer().document());
    }

    @Test
    void clearsDocumentStateOnNewDocument() {
        final TermGenerator gen = TermGeneratorTest.stemmer();
        gen.indexText("hello.");
        gen.document(DatabaseFactory.document());
        Assertions.assertEquals(1, gen.nextPosition());
    }

    @Test
    void clearsIdentifierOnNewDocument() {
        final TermGenerator gen = TermGeneratorTest.stemmer();
        gen.lastDocId(4L);
        gen.document(DatabaseFactory.document());
        Assertions.assertEquals(-1L, gen.lastDocId());
    }

    @Test
    void startsWithoutDocument() {
        Assertions.assertNull(new TermGenerator().document());
    }

    @Test
    void startsWithoutStopper() {
        Assertions.assertNull(new TermGenerator().stopper());
    }

    @Test
    void startsWithoutStemmer() {
        Assertions.assertNull(new TermGenerator().stemmer());
    }

    @Test
    void startsWithoutStrategy() {
        Assertions.assertEquals(
            StemStrategy.NONE, new TermGenerator().strategy()
        );
    }

    @Test
    void startsWithoutPrefix() {
        Assertions.assertEquals("", new TermGenerator().prefix());
    }

    @Test
    void startsWithoutRange() {
        Assertions.assertEquals(-1, new TermGenerator().rangeStart());
    }

    @Test
    void refusesNullDocument() {
        final TermGenerator gen = new TermGenerator();
        Assertions.assertThrows(
            NullPointerException.class, () -> gen.document(null)
        );
    }

    private static String stem(final String word) {
        final String result;
        if (word.startsWith("search")) {
            result = "search";
        } else {
            result = word;
        }
        return result;
    }

    private static Document prefixed(final String prefix, final String text) {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.prefix(prefix);
        gen.indexText(text);
        return doc;
    }

    private static String ranged(final int start, final String text) {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        if (start >= 0) {
            gen.rangeStart(start);
        }
        gen.indexTextRange(text, 0);
        return doc.value(new ValueNo(0)).orElse("NONE");
    }

    private static TermGenerator stemmer() {
        final TermGenerator gen = new TermGenerator();
        gen.document(DatabaseFactory.document());
        gen.prefix("X");
        gen.stopper(Stopper.of("X"));
        gen.stemmer(new TermGeneratorTest.Named(), StemStrategy.ALL);
        gen.rangeStart(4);
        return gen;
    }

    private static Document stemmed(final StemStrategy mode, final String text) {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.stemmer(TermGeneratorTest::stem, mode);
        gen.indexText(text);
        return doc;
    }

    private static Document generated(final String text) {
        final TermGenerator gen = new TermGenerator();
        final Document doc = DatabaseFactory.document();
        gen.document(doc);
        gen.indexText(text);
        return doc;
    }

    private static TermCount frequency(final Document doc, final String term) {
        TermCount result = TermCount.ZERO;
        try (TermIterator terms = doc.termList()) {
            while (terms.hasNext()) {
                if (terms.next().equals(term)) {
                    result = terms.wdf();
                }
            }
        }
        return result;
    }

    private static List<TermPos> positions(
        final Document doc, final String term
    ) {
        final List<TermPos> found = new ArrayList<>(0);
        try (TermIterator terms = doc.termList()) {
            while (terms.hasNext()) {
                if (terms.next().equals(term)) {
                    try (PositionIterator places = terms.positions()) {
                        while (places.hasNext()) {
                            found.add(places.next());
                        }
                    }
                }
            }
        }
        return found;
    }

    private static final class Named implements Stemmer {

        @Override
        public String stem(final String word) {
            return TermGeneratorTest.stem(word);
        }

        @Override
        public String name() {
            return "s";
        }
    }
}
