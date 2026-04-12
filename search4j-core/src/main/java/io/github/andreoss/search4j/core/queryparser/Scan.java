/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.ParseFlag;
import io.github.andreoss.search4j.core.types.StemStrategy;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Map;
import java.util.Set;

/**
 * Reads one word of a query: an acronym, or word characters joined by
 * infix characters, with an optional suffix and trailing wildcard.
 *
 * @since 0.1.0
 */
final class Scan {

    /**
     * Most suffix characters kept after a word.
     */
    private static final int SUFFIX_MAX = 3;

    /**
     * Words read as operators.
     */
    private static final Map<String, Lexeme.Kind> OPERATORS = Map.of(
        "AND", Lexeme.Kind.AND, "NOT", Lexeme.Kind.NOT,
        "XOR", Lexeme.Kind.XOR, "SYN", Lexeme.Kind.SYN,
        "OR", Lexeme.Kind.OR, "ADJ", Lexeme.Kind.ADJ,
        "NEAR", Lexeme.Kind.NEAR
    );

    /**
     * Character types of a first letter that allows stemming.
     */
    private static final Set<Integer> STEMMED = Set.of(
        (int) Character.LOWERCASE_LETTER, (int) Character.TITLECASE_LETTER,
        (int) Character.MODIFIER_LETTER, (int) Character.OTHER_LETTER
    );

    /**
     * Query being read.
     */
    private final Lexer source;

    /**
     * Parse settings.
     */
    private final Setup setup;

    /**
     * The word read.
     */
    private String found;

    /**
     * Index of the next code point.
     */
    private int pos;

    /**
     * Number of characters in the word.
     */
    private int count;

    /**
     * True when the word is an acronym.
     */
    private boolean upper;

    /**
     * True when a wildcard ends the word.
     */
    private boolean wild;

    /**
     * Creates a scan starting at a word character.
     *
     * @param source Query being read
     * @param start Index of the first character
     * @param setup Parse settings
     */
    Scan(final Lexer source, final int start, final Setup setup) {
        this.source = source;
        this.pos = start;
        this.setup = setup;
        this.found = "";
    }

    /**
     * Reads the word.
     *
     * @return This scan, finished
     */
    Scan run() {
        if (Chars.capital(this.source.at(this.pos))) {
            this.acronymic();
        }
        if (!this.upper) {
            this.plain();
            this.suffixed();
            if (this.setup.has(ParseFlag.WILDCARD) && this.at(this.pos) == '*') {
                this.pos = this.pos + 1;
                this.wild = true;
            }
        }
        return this;
    }

    /**
     * The index after the word.
     *
     * @return Index of the next code point
     */
    int end() {
        return this.pos;
    }

    /**
     * The length of the word.
     *
     * @return Number of characters
     */
    int chars() {
        return this.count;
    }

    /**
     * Whether a wildcard ends the word.
     *
     * @return True for a wildcard word
     */
    boolean wildcard() {
        return this.wild;
    }

    /**
     * The operator the word names.
     *
     * @return Operator kind, or the end kind when the word is no operator
     */
    Lexeme.Kind operator() {
        Lexeme.Kind kind = Lexeme.Kind.END;
        if (!this.wild && !this.upper && this.setup.has(ParseFlag.BOOLEAN)) {
            String name = this.found;
            if (this.setup.has(ParseFlag.BOOLEAN_ANY_CASE)) {
                name = Scan.ascii(name);
            }
            kind = Scan.OPERATORS.getOrDefault(name, Lexeme.Kind.END);
        }
        return kind;
    }

    /**
     * The word as a token payload.
     *
     * @param termpos Position of the word in the query
     * @return Lower-cased word with its stemming decision
     */
    Word toWord(final int termpos) {
        final StringBuilder low = new StringBuilder(this.found.length());
        this.found.codePoints().map(Character::toLowerCase)
            .forEach(low::appendCodePoint);
        return new Word(low.toString(), this.stemming(), new TermPos(termpos));
    }

    private StemStrategy stemming() {
        StemStrategy stem = this.setup.strategy();
        if (stem != StemStrategy.NONE && this.unstem(stem)) {
            stem = StemStrategy.NONE;
        }
        return stem;
    }

    private boolean unstem(final StemStrategy stem) {
        return this.setup.unstemmed() || this.wild
            || Scan.selective(stem) && !this.stemmable();
    }

    private boolean stemmable() {
        return this.letter(this.found.codePointAt(0))
            && !Chars.preventer(this.at(this.pos));
    }

    private boolean letter(final int chr) {
        final int type = Character.getType(chr);
        return Scan.STEMMED.contains(type)
            || type == Character.UPPERCASE_LETTER
                && this.setup.has(ParseFlag.STEM_CAPITALS);
    }

    private static boolean selective(final StemStrategy stem) {
        return stem == StemStrategy.SOME || stem == StemStrategy.SOME_FULL_POS;
    }

    private static String ascii(final String word) {
        final StringBuilder high = new StringBuilder(word.length());
        for (int idx = 0; idx < word.length(); ++idx) {
            final char chr = word.charAt(idx);
            if (chr >= 'a' && chr <= 'z') {
                high.append((char) (chr - 'a' + 'A'));
            } else {
                high.append(chr);
            }
        }
        return high.toString();
    }

    private void acronymic() {
        final StringBuilder letters = new StringBuilder(8);
        int next = this.pos;
        boolean more = true;
        while (more) {
            letters.appendCodePoint(this.source.at(next));
            next = next + 1;
            more = this.at(next) == '.' && Chars.capital(this.at(next + 1));
            if (more || this.at(next) == '.') {
                next = next + 1;
            }
        }
        if (letters.length() > 1 && !Chars.word(this.at(next))) {
            this.pos = next;
            this.found = letters.toString();
            this.count = letters.length();
            this.upper = true;
        }
    }

    private void plain() {
        final StringBuilder text = new StringBuilder(16);
        int prev = this.source.at(this.pos);
        text.appendCodePoint(prev);
        this.count = 1;
        this.pos = this.pos + 1;
        boolean more = true;
        while (more && this.pos < this.source.size()) {
            int chr = this.source.at(this.pos);
            if (!Chars.word(chr)) {
                chr = this.joined(prev, chr);
            }
            more = chr != 0;
            if (more && chr != Chars.IGNORE) {
                text.appendCodePoint(chr);
                this.count = this.count + 1;
                prev = chr;
            }
            if (more) {
                this.pos = this.pos + 1;
            }
        }
        this.found = text.toString();
    }

    private int joined(final int prev, final int chr) {
        final int result;
        final int next = this.at(this.pos + 1);
        if (Chars.word(next) && Chars.digit(prev) && Chars.digit(next)) {
            result = Chars.digitInfix(chr);
        } else if (Chars.word(next)) {
            result = Chars.infix(chr);
        } else {
            result = 0;
        }
        return result;
    }

    private void suffixed() {
        int next = this.pos;
        final StringBuilder tail = new StringBuilder(Scan.SUFFIX_MAX + 1);
        while (Chars.suffix(this.at(next)) && tail.length() <= Scan.SUFFIX_MAX) {
            tail.appendCodePoint(this.at(next));
            next = next + 1;
        }
        if (tail.length() > 0 && tail.length() <= Scan.SUFFIX_MAX
            && !Chars.word(this.at(next))) {
            this.found = this.found.concat(tail.toString());
            this.count = this.count + tail.length();
            this.pos = next;
        }
    }

    private int at(final int idx) {
        final int chr;
        if (idx >= this.source.size()) {
            chr = 0;
        } else {
            chr = this.source.at(idx);
        }
        return chr;
    }
}
