/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term generation from text: words, prefixes, positions and n-grams.
 */
package io.github.andreoss.search4j.core.termgen;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.DocumentTerms;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Locale;
import java.util.Objects;

/**
 * Turns text into document terms, positions and value ranges.
 *
 * <p>Each word contributes a positional term; a prefix ending in an
 * underscore also produces a field term, and one ending in a colon spans the
 * next two words. A sentence end closes the span with an end marker. Text
 * that starts with a flag prefix decides how that word is stemmed.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "PMD.ConstructorShouldDoInitialization",
    "PMD.TooManyMethods",
    "PMD.UncommentedEmptyConstructor",
    "PMD.UseVarargs",
    "PMD.LiteralsFirstInComparisons",
    "PMD.UnnecessaryLocalRule"
})
public final class TermGenerator {

    /**
     * Document receiving the terms.
     */
    private Document document;

    /**
     * Prefix of positional terms.
     */
    private String prefix = "";

    /**
     * Stop words, null when every word is indexed.
     */
    private Stopper stopper;

    /**
     * Stemmer, null when words are indexed as they are.
     */
    private Stemmer stemmer;

    /**
     * Stemming strategy.
     */
    private StemStrategy strategy = StemStrategy.NONE;

    /**
     * Lower bound of the value range kept, -1 for none.
     */
    private int range = -1;

    /**
     * Position of the next positional term.
     */
    private long position = 1L;

    /**
     * Last document identifier reported.
     */
    private long latest = -1;

    /**
     * Creates a generator with no document and no configuration.
     */
    public TermGenerator() {
    }

    /**
     * Sets the document receiving the terms, discarding the state of the
     * previous one.
     *
     * @param target Document to fill
     */
    public void document(final Document target) {
        this.document = Objects.requireNonNull(target, "Document");
        this.position = 1L;
        this.latest = -1;
        this.range = -1;
    }

    /**
     * Document receiving the terms.
     *
     * @return Document set, null when none
     */
    public Document document() {
        return this.document;
    }

    /**
     * Sets the prefix of positional terms.
     *
     * @param text Prefix, empty for none
     */
    public void prefix(final String text) {
        this.prefix = Objects.requireNonNull(text, "Prefix");
    }

    /**
     * Prefix of positional terms.
     *
     * @return Prefix, empty when none
     */
    public String prefix() {
        return this.prefix;
    }

    /**
     * Sets the stop words.
     *
     * @param stop Stopper, null to index every word
     */
    public void stopper(final Stopper stop) {
        this.stopper = stop;
    }

    /**
     * Stop words.
     *
     * @return Stopper, null when every word is indexed
     */
    public Stopper stopper() {
        return this.stopper;
    }

    /**
     * Sets the stemmer and the strategy that applies it.
     *
     * @param reduce Stemmer, null for none
     * @param mode Stemming strategy
     */
    public void stemmer(final Stemmer reduce, final StemStrategy mode) {
        this.stemmer = reduce;
        this.strategy = Objects.requireNonNull(mode, "Stem strategy");
    }

    /**
     * Stemmer.
     *
     * @return Stemmer, null when words are indexed as they are
     */
    public Stemmer stemmer() {
        return this.stemmer;
    }

    /**
     * Stemming strategy.
     *
     * @return Strategy in force
     */
    public StemStrategy strategy() {
        return this.strategy;
    }

    /**
     * Sets the lower bound of the value range kept while indexing numbers.
     *
     * @param bound Lower bound, -1 to keep no range
     */
    public void rangeStart(final int bound) {
        this.range = bound;
    }

    /**
     * Lower bound of the value range kept while indexing numbers.
     *
     * @return Lower bound, -1 when no range is kept
     */
    public int rangeStart() {
        return this.range;
    }

    /**
     * Indexes text into the document.
     *
     * @param text Text to index
     */
    public void indexText(final String text) {
        final DocumentTerms terms = this.requireDocument();
        char pending = ' ';
        for (final WordBreaker.Word word : WordBreaker.words(text)) {
            final String raw = TermGenerator.truncate(word.text());
            final String lowered = raw.toLowerCase(Locale.ROOT);
            if (TermGenerator.flagged(text, lowered)) {
                pending = lowered.charAt(0);
                continue;
            }
            this.indexWord(
                terms, pending, lowered, raw, word.sentenceEnd()
            );
            pending = ' ';
            this.indexField(terms);
            if (word.sentenceEnd()) {
                this.indexEnd(terms);
            }
        }
    }

    /**
     * Indexes text as a value range in a slot.
     *
     * @param text Text to index
     * @param slot Value slot receiving the range
     */
    public void indexTextRange(final String text, final int slot) {
        final Document target = this.requireDocument();
        final int[] bounds = TermGenerator.numericBounds(text);
        if (bounds.length > 0) {
            final int lower;
            if (this.range < 0) {
                lower = bounds[0];
            } else {
                lower = this.range;
            }
            TermGenerator.addBounds(target, slot, lower, bounds);
            this.range = -1;
        } else {
            for (final WordBreaker.Word word : WordBreaker.words(text)) {
                final int[] single = TermGenerator.numericBounds(word.text());
                if (single.length == 2) {
                    TermGenerator.addBounds(target, slot, single[0], single);
                }
            }
        }
    }

    /**
     * Adds a term without a position.
     *
     * @param term Term to add
     * @param frequency Amount added to the frequency
     */
    public void indexTerm(final String term, final TermCount frequency) {
        this.requireDocument().addTerm(
            TermGenerator.reduced(term, false, this.strategy, this.stemmer),
            frequency
        );
    }

    /**
     * Adds a term at the next position.
     *
     * @param term Term to add
     * @param frequency Amount added to the frequency
     */
    public void indexTermAtNextPosition(
        final String term, final TermCount frequency
    ) {
        this.requireDocument().addPosting(
            TermGenerator.reduced(term, false, this.strategy, this.stemmer),
            new TermPos(this.position), frequency
        );
        this.position = TermPos.wrap(this.position + 1L).value();
    }

    /**
     * Next position a term would take.
     *
     * @return Position of the next positional term
     */
    public long nextPosition() {
        return this.position;
    }

    /**
     * Sets the next position a term would take.
     *
     * @param next Position of the next positional term, from 1 to the
     *  unsigned 32-bit maximum
     */
    public void nextPosition(final long next) {
        if (next < 1L || next > TermPos.MAX.value()) {
            throw new InvalidArgumentError(
                "Next position must be positive and fit 32 bits"
            );
        }
        this.position = next;
    }

    /**
     * Last document identifier reported.
     *
     * @return Identifier, -1 when none was reported
     */
    public long lastDocId() {
        return this.latest;
    }

    /**
     * Reports the identifier of the document being indexed.
     *
     * @param docid Identifier to record
     */
    public void lastDocId(final long docid) {
        this.latest = docid;
    }

    /**
     * Field prefix carried by the first word of text.
     *
     * @param text Text to scan
     * @return Field prefix ending in an underscore, empty when none
     */
    public static String fieldPrefix(final String text) {
        String result = "";
        int index = 0;
        while (index < text.length() && WordBreaker.part(text.charAt(index))) {
            ++index;
        }
        if (index > 0 && index < text.length() && text.charAt(index) == '_') {
            result = text.substring(0, index + 1);
        }
        return result;
    }

    /**
     * Finds the first and last number in text.
     *
     * @param text Text to scan
     * @return First and last number, empty when the text holds none
     */
    public static int[] numericBounds(final String text) {
        int first = 0;
        int last = 0;
        boolean found = false;
        for (final WordBreaker.Word word : WordBreaker.words(text)) {
            if (TermGenerator.numeric(word.text())) {
                final int number = Integer.parseInt(word.text());
                if (!found) {
                    first = number;
                    found = true;
                }
                last = number;
            }
        }
        final int[] bounds;
        if (found) {
            bounds = new int[] {first, last};
        } else {
            bounds = new int[0];
        }
        return bounds;
    }

    private Document requireDocument() {
        if (this.document == null) {
            throw new InvalidOperationError("Term generator needs a document");
        }
        return this.document;
    }

    private void indexWord(
        final DocumentTerms terms, final char flag, final String lowered,
        final String word, final boolean atcap
    ) {
        final boolean asked = TermGenerator.asked(flag);
        final TermPos place = new TermPos(this.position);
        if (!this.rejected(lowered)) {
            terms.addPosting(this.prefix + lowered, place, TermCount.ONE);
        }
        final boolean stem = flag == 's' || flag == 'x'
            || !asked && TermGenerator.reduces(this.strategy, word, atcap);
        if (stem) {
            this.indexStem(terms, flag, lowered, place);
        }
        this.position = TermPos.wrap(this.position + 1L).value();
    }

    private void indexStem(
        final DocumentTerms terms, final char flag, final String lowered,
        final TermPos place
    ) {
        if (this.stemmer != null) {
            final String marked = this.stemmer.stem(lowered);
            final boolean mark = flag == 'x'
                || flag != 's' && flag != 'z'
                && this.strategy == StemStrategy.ALL_Z;
            String term = this.prefix + marked;
            if (mark) {
                term = this.prefix + marked + 'Z';
            }
            if (!marked.isEmpty() && !this.rejected(term)) {
                terms.addPosting(term, place, TermCount.ONE);
            }
        }
    }

    private void indexEnd(final DocumentTerms terms) {
        final TermPos end = new TermPos(this.position);
        terms.addPosting(
            String.join("", this.prefix, "xe"), end, TermCount.ONE
        );
        terms.addTerm(
            String.join("", this.prefix, "xsentence"), TermCount.ONE
        );
        this.position = TermPos.wrap(this.position + 1L).value();
    }

    private void indexField(final DocumentTerms terms) {
        if (this.prefix.endsWith("_")) {
            terms.addTerm(
                this.prefix.substring(0, this.prefix.length() - 1),
                TermCount.ONE
            );
        } else if (this.prefix.endsWith(":")) {
            final long upper = this.position;
            terms.addTerm(
                String.format(
                    "%s%d..%d",
                    this.prefix.substring(0, this.prefix.length() - 1),
                    upper, upper + 1
                ),
                TermCount.ONE
            );
        }
    }

    private boolean rejected(final String term) {
        return this.stopper != null && this.stopper.stop(term);
    }

    private static boolean flagged(final String text, final String lowered) {
        final char lead = lowered.charAt(0);
        return lowered.length() == 1 && TermGenerator.flag(lead)
            && TermGenerator.following(text, lowered);
    }

    private static boolean following(final String text, final String word) {
        final int pos = text.indexOf(word);
        final int after = pos + word.length();
        return pos >= 0 && after < text.length() && text.charAt(after) == '_';
    }

    private static String reduced(
        final String term, final boolean atcap, final StemStrategy mode,
        final Stemmer reduce
    ) {
        final String lowered = term.toLowerCase(Locale.ROOT);
        String result = lowered;
        if (reduce != null && TermGenerator.reduces(mode, lowered, atcap)) {
            result = reduce.stem(lowered);
        }
        return result;
    }

    private static boolean reduces(
        final StemStrategy mode, final String word, final boolean atcap
    ) {
        return TermGenerator.every(mode) || mode == StemStrategy.SOME
            && (atcap || TermGenerator.upper(word));
    }

    private static boolean every(final StemStrategy mode) {
        return mode == StemStrategy.ALL || mode == StemStrategy.ALL_Z
            || mode == StemStrategy.SOME_FULL_POS;
    }

    private static boolean asked(final char flag) {
        return flag == 's' || flag == 'r' || flag == 'x' || flag == 'z';
    }

    private static boolean flag(final char character) {
        final char lowered = Character.toLowerCase(character);
        return TermGenerator.asked(lowered)
            || lowered == 'z' || lowered == 'a';
    }

    private static boolean upper(final String text) {
        boolean letter = false;
        boolean lower = false;
        for (int index = 0; index < text.length(); ++index) {
            final char character = text.charAt(index);
            letter = letter || Character.isLetter(character);
            lower = lower || Character.isLowerCase(character);
        }
        return letter && !lower;
    }

    private static String truncate(final String text) {
        final String result;
        if (text.length() <= WordBreaker.MAX_WORD) {
            result = text;
        } else {
            result = text.substring(0, WordBreaker.MAX_WORD);
        }
        return result;
    }

    private static boolean numeric(final String text) {
        boolean result = !text.isEmpty();
        for (int index = 0; result && index < text.length(); ++index) {
            result = Character.isDigit(text.charAt(index));
        }
        if (result) {
            result = text.length() < 10
                || text.length() == 10 && text.compareTo("2147483647") <= 0;
        }
        return result;
    }

    private static void addBounds(
        final Document target, final int slot, final int lower,
        final int[] bounds
    ) {
        target.addValue(
            new ValueNo(slot), String.format("%d..%d", lower, bounds[1])
        );
    }
}
