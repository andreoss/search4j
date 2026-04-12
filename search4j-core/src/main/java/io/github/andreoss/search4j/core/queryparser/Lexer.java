/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Query parser grammar and parsing.
 */
package io.github.andreoss.search4j.core.queryparser;

import io.github.andreoss.search4j.core.types.ParseFlag;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a query into tokens, tracking quotes, brackets, word groups and
 * phrases joined by punctuation.
 *
 * @since 0.1.0
 */
final class Lexer {

    /**
     * Query text.
     */
    private final String query;

    /**
     * Parse settings.
     */
    private final Setup setup;

    /**
     * Tokens read so far.
     */
    private final List<Lexeme> out;

    /**
     * Query code points.
     */
    private int[] text;

    /**
     * Index of the next code point.
     */
    private int pos;

    /**
     * Position given to the next word.
     */
    private int termpos;

    /**
     * Current lexing mode.
     */
    private Mode mode;

    /**
     * Previous significant character.
     */
    private int newprev;

    /**
     * Brackets opened and not closed.
     */
    private int depth;

    /**
     * What the previous token was.
     */
    private After after;

    /**
     * True once the rest of the query is ignored.
     */
    private boolean stopped;

    /**
     * Creates a lexer.
     *
     * @param query Query text
     * @param setup Parse settings
     */
    Lexer(final String query, final Setup setup) {
        this.query = query;
        this.setup = setup;
        this.out = new ArrayList<>(16);
        this.text = new int[0];
        this.termpos = 1;
        this.mode = Mode.DEFAULT;
        this.newprev = ' ';
        this.after = After.NONE;
    }

    /**
     * Reads every token of the query.
     *
     * @return Tokens ending with the end token
     */
    List<Lexeme> lex() {
        this.text = this.query.codePoints().toArray();
        while (!this.stopped && this.more()) {
            this.step();
        }
        if (this.mode == Mode.IN_QUOTES) {
            this.emit(Lexeme.Kind.QUOTE);
        }
        while (this.depth > 0) {
            this.emit(Lexeme.Kind.KET);
            this.depth = this.depth - 1;
        }
        this.emit(Lexeme.Kind.END);
        return List.copyOf(this.out);
    }

    /**
     * The code point at an index.
     *
     * @param idx Index
     * @return Code point
     */
    int at(final int idx) {
        return this.text[idx];
    }

    /**
     * The number of code points.
     *
     * @return Query length in code points
     */
    int size() {
        return this.text.length;
    }

    private void step() {
        final After last = this.after;
        this.after = After.NONE;
        if (last == After.OPERATOR) {
            this.mode = Mode.DEFAULT;
        } else if (last == After.NONE && this.mode == Mode.EXPLICIT_SYNONYM) {
            this.mode = Mode.DEFAULT;
        }
        if (this.mode == Mode.IN_PHRASED_TERM) {
            this.mode = Mode.DEFAULT;
        }
        if (Chars.space(this.cur())) {
            this.newprev = ' ';
            this.skipSpaces();
        }
        if (this.more()) {
            if (Chars.word(this.cur())) {
                this.newprev = 'A';
                this.words(last == After.NEEDS_TERM);
            } else {
                this.punctuation(last != After.NONE);
            }
        }
    }

    private void punctuation(final boolean operator) {
        final int prev = this.newprev;
        final int chr = this.text[this.pos];
        this.pos = this.pos + 1;
        this.newprev = chr;
        if (this.grouped()) {
            this.mode = Mode.DEFAULT;
        }
        if (Chars.quote(chr)) {
            this.quote(chr);
        } else if (chr == '+' || chr == '-') {
            this.sign(chr, prev, operator);
        } else if (chr == '(') {
            this.open(prev);
        } else if (chr == ')') {
            this.close();
        } else if (chr == '~') {
            this.tilde(prev);
        }
    }

    private void quote(final int chr) {
        boolean skip = false;
        if (this.mode == Mode.DEFAULT) {
            this.skipSpaces();
            skip = this.empty();
        }
        if (!skip && this.setup.has(ParseFlag.PHRASE)) {
            if (chr == '"' && this.more() && this.cur() == '"') {
                this.pos = this.pos + 1;
            } else {
                this.toggle();
            }
        }
    }

    private void toggle() {
        this.emit(Lexeme.Kind.QUOTE);
        if (this.mode == Mode.DEFAULT) {
            this.mode = Mode.IN_QUOTES;
        } else {
            this.mode = Mode.DEFAULT;
        }
    }

    private boolean empty() {
        final boolean skip;
        if (this.more()) {
            skip = Chars.quote(this.cur());
            if (skip) {
                this.consume();
            }
        } else {
            this.stopped = true;
            skip = true;
        }
        return skip;
    }

    private void sign(final int chr, final int prev, final boolean operator) {
        if (this.more()) {
            if (Lexer.opens(prev, "(")) {
                this.signed(chr, operator);
            }
        } else {
            this.stopped = true;
        }
    }

    private void signed(final int chr, final boolean operator) {
        final int next = this.cur();
        if (Chars.space(next) || next == '+' || next == '-') {
            this.consume();
        } else if (this.mode == Mode.DEFAULT
            && this.setup.has(ParseFlag.LOVEHATE)) {
            this.emit(Lexer.sentiment(chr, operator));
            this.after = After.NEEDS_TERM;
        }
    }

    private void open(final int prev) {
        this.skipSpaces();
        if (this.more()) {
            if (Lexer.opens(prev, "()+-")) {
                this.bracket();
            }
        } else {
            this.stopped = true;
        }
    }

    private void bracket() {
        if (this.cur() == ')') {
            this.consume();
        } else if (this.mode == Mode.DEFAULT
            && this.setup.has(ParseFlag.BOOLEAN)) {
            this.depth = this.depth + 1;
            this.emit(Lexeme.Kind.BRA);
        }
    }

    private void close() {
        if (this.mode == Mode.DEFAULT && this.setup.has(ParseFlag.BOOLEAN)) {
            if (this.depth > 0) {
                this.depth = this.depth - 1;
            }
            this.emit(Lexeme.Kind.KET);
        }
    }

    private void tilde(final int prev) {
        if (this.more()) {
            if (this.mode == Mode.DEFAULT && this.setup.has(ParseFlag.SYNONYM)
                && Lexer.opens(prev, "+-(") && this.leads()) {
                this.expand();
            }
        } else {
            this.stopped = true;
        }
    }

    private boolean leads() {
        return Chars.word(this.cur()) || Chars.quote(this.cur());
    }

    private void expand() {
        this.emit(Lexeme.Kind.SYNONYM);
        this.mode = Mode.EXPLICIT_SYNONYM;
        if (Chars.quote(this.cur())) {
            this.pos = this.pos + 1;
            this.skipSpaces();
            this.quoted();
        } else {
            this.after = After.NEEDS_TERM;
        }
    }

    private void quoted() {
        if (!this.empty()) {
            this.emit(Lexeme.Kind.QUOTE);
            this.mode = Mode.IN_QUOTES;
        }
    }

    private void words(final boolean needs) {
        boolean phrased = true;
        while (phrased) {
            phrased = false;
            final Scan scan = new Scan(this, this.pos, this.setup).run();
            this.pos = scan.end();
            if (!this.keyword(scan) && this.word(scan) && this.more()) {
                phrased = this.follow(needs);
            }
        }
    }

    private boolean follow(final boolean needs) {
        boolean phrased = false;
        if (Chars.generator(this.cur())) {
            this.skipGenerators();
            phrased = this.more() && Chars.word(this.cur());
            if (phrased) {
                this.mode = Mode.IN_PHRASED_TERM;
            }
        } else if (this.plainMode()) {
            this.regroup(needs);
        }
        return phrased;
    }

    private void regroup(final boolean needs) {
        final boolean was = this.grouped();
        this.mode = Mode.DEFAULT;
        if (!needs && Chars.space(this.cur())) {
            this.newprev = ' ';
            this.skipSpaces();
            if (this.more() && Chars.word(this.cur())) {
                this.mode = Lexer.grouping(was);
            }
        }
    }

    private boolean keyword(final Scan scan) {
        boolean found = false;
        if (this.plainMode()) {
            found = this.operator(scan.operator());
        }
        if (found) {
            this.after = After.OPERATOR;
        }
        return found;
    }

    private boolean operator(final Lexeme.Kind kind) {
        final boolean found;
        if (kind == Lexeme.Kind.ADJ || kind == Lexeme.Kind.NEAR) {
            found = this.window(kind);
        } else if (kind == Lexeme.Kind.END) {
            found = false;
        } else {
            this.emit(kind);
            found = true;
        }
        return found;
    }

    private boolean window(final Lexeme.Kind kind) {
        boolean found = true;
        if (this.more() && this.cur() == '/') {
            int next = this.pos + 1;
            long width = 0L;
            while (next < this.text.length && Chars.ascii(this.text[next])) {
                width = width * 10L + this.text[next] - '0';
                next = next + 1;
            }
            if (width != 0L
                && (next == this.text.length || Chars.space(this.text[next]))) {
                this.pos = next;
                this.out.add(new Lexeme(kind, Lexeme.BLANK, (int) width));
            } else {
                found = false;
            }
        } else {
            this.emit(kind);
        }
        return found;
    }

    private boolean word(final Scan scan) {
        final Word word = scan.toWord(this.termpos);
        this.termpos = this.termpos + 1;
        final boolean further;
        if (scan.wildcard()) {
            this.ungroup();
            this.out.add(Lexeme.of(Lexeme.Kind.WILD_TERM, word));
            further = false;
        } else if (this.partial(scan)) {
            this.ungroup();
            this.out.add(Lexeme.of(Lexeme.Kind.PARTIAL_TERM, word));
            further = false;
        } else if (this.mode == Mode.IN_PHRASED_TERM) {
            this.out.add(Lexeme.of(Lexeme.Kind.PHR_TERM, word));
            further = true;
        } else {
            further = this.plain(word);
        }
        return further;
    }

    private boolean partial(final Scan scan) {
        return !this.more() && this.plainMode()
            && this.setup.has(ParseFlag.PARTIAL) && scan.chars() >= 2;
    }

    private boolean plain(final Word word) {
        if (this.grouped() && this.more() && Chars.generator(this.cur())) {
            this.split();
        }
        final boolean further;
        if (this.grouped()) {
            this.mode = Mode.IN_GROUP2;
            this.out.add(Lexeme.of(Lexeme.Kind.GROUP_TERM, word));
            further = true;
        } else {
            this.out.add(Lexeme.of(Lexeme.Kind.TERM, word));
            further = this.mode == Mode.DEFAULT;
        }
        return further;
    }

    private void split() {
        int next = this.pos;
        while (next < this.text.length && Chars.generator(this.text[next])) {
            next = next + 1;
        }
        if (next < this.text.length && Chars.word(this.text[next])) {
            this.mode = Mode.DEFAULT;
        }
    }

    private void ungroup() {
        if (this.mode == Mode.IN_GROUP2) {
            this.emit(Lexeme.Kind.EMPTY_GROUP_OK);
        }
        if (this.grouped()) {
            this.mode = Mode.DEFAULT;
        }
    }

    private boolean plainMode() {
        return this.mode == Mode.DEFAULT || this.grouped();
    }

    private boolean grouped() {
        return this.mode == Mode.IN_GROUP || this.mode == Mode.IN_GROUP2;
    }

    private void emit(final Lexeme.Kind kind) {
        this.out.add(Lexeme.of(kind));
    }

    private boolean more() {
        return this.pos < this.text.length;
    }

    private int cur() {
        return this.text[this.pos];
    }

    private void consume() {
        this.newprev = this.cur();
        this.pos = this.pos + 1;
    }

    private void skipSpaces() {
        while (this.more() && Chars.space(this.cur())) {
            this.pos = this.pos + 1;
        }
    }

    private void skipGenerators() {
        while (this.more() && Chars.generator(this.cur())) {
            this.pos = this.pos + 1;
        }
    }

    private static boolean opens(final int prev, final String allowed) {
        return prev <= ' ' || allowed.indexOf(prev) >= 0;
    }

    private static Lexeme.Kind sentiment(final int chr, final boolean operator) {
        final Lexeme.Kind kind;
        if (chr == '+') {
            kind = Lexeme.Kind.LOVE;
        } else if (operator) {
            kind = Lexeme.Kind.HATE_AFTER_AND;
        } else {
            kind = Lexeme.Kind.HATE;
        }
        return kind;
    }

    private static Mode grouping(final boolean was) {
        final Mode result;
        if (was) {
            result = Mode.IN_GROUP2;
        } else {
            result = Mode.IN_GROUP;
        }
        return result;
    }

    /**
     * Lexing modes.
     *
     * @since 0.1.0
     */
    private enum Mode {

        /**
         * Outside quotes, groups and phrases.
         */
        DEFAULT,

        /**
         * Inside a quoted phrase.
         */
        IN_QUOTES,

        /**
         * After a phrase character joining two words.
         */
        IN_PHRASED_TERM,

        /**
         * After white space following a word.
         */
        IN_GROUP,

        /**
         * After a word of a group.
         */
        IN_GROUP2,

        /**
         * After a synonym operator.
         */
        EXPLICIT_SYNONYM
    }

    /**
     * What the previous token was.
     *
     * @since 0.1.0
     */
    private enum After {

        /**
         * Anything but an operator.
         */
        NONE,

        /**
         * A binary operator.
         */
        OPERATOR,

        /**
         * An operator that needs a following word.
         */
        NEEDS_TERM
    }
}
