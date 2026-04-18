/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.query.Wildcard;
import io.github.andreoss.search4j.core.types.WildcardPattern;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Test of a term against a wildcard pattern: a fixed prefix, a variable
 * middle where a star matches any run of bytes and a question mark matches
 * one character, and a fixed suffix.
 *
 * @since 0.10.0
 */
final class Glob {

    /**
     * Largest length bound.
     */
    private static final long UNBOUNDED = Long.MAX_VALUE;

    /**
     * Pattern bytes.
     */
    private final byte[] pattern;

    /**
     * Characters with a special meaning.
     */
    private final WildcardPattern features;

    /**
     * Start of the variable part of the pattern.
     */
    private final int head;

    /**
     * End of the variable part of the pattern.
     */
    private final int tail;

    /**
     * Lengths of the matching terms.
     */
    private final GlobBounds bounds;

    private Glob(
        final byte[] pattern, final WildcardPattern glob, final int head,
        final int tail, final GlobBounds bounds
    ) {
        this.pattern = pattern;
        this.features = glob;
        this.head = head;
        this.tail = tail;
        this.bounds = bounds;
    }

    static Glob of(final Wildcard spec) {
        final byte[] bytes = spec.pattern().getBytes(StandardCharsets.UTF_8);
        final WildcardPattern glob = spec.glob();
        final Glob result;
        if (glob.code() == 0) {
            result = new Glob(
                bytes, glob, bytes.length, bytes.length,
                new GlobBounds(bytes.length, Glob.UNBOUNDED, Glob.UNBOUNDED)
            );
        } else {
            int head = 0;
            while (head != bytes.length && !Glob.special(glob, bytes[head])) {
                head = head + 1;
            }
            int tail = bytes.length;
            while (tail > head && !Glob.special(glob, bytes[tail - 1])) {
                tail = tail - 1;
            }
            result = new Glob(bytes, glob, head, tail, Glob.measure(bytes, glob, head, tail));
        }
        return result;
    }

    byte[] prefix() {
        return Arrays.copyOf(this.pattern, this.head);
    }

    boolean test(final byte[] candidate) {
        final int fix = this.pattern.length - this.tail;
        boolean result = candidate.length >= this.bounds.shortest()
            && candidate.length <= this.bounds.longest()
            && Arrays.equals(
                candidate, candidate.length - Math.min(fix, candidate.length),
                candidate.length, this.pattern, this.tail, this.pattern.length
            );
        if (result && candidate.length >= this.bounds.check()) {
            result = this.matches(candidate, this.head, candidate.length - fix, this.head);
        }
        return result;
    }

    private boolean matches(
        final byte[] candidate, final int pos, final int end, final int index
    ) {
        final boolean result;
        if (index == this.tail) {
            result = pos == end;
        } else if (this.features.multi() && this.pattern[index] == '*') {
            result = this.star(candidate, pos, end, index + 1);
        } else {
            final int width = this.width(candidate, pos, end, index);
            result = width > 0 && this.matches(candidate, pos + width, end, index + 1);
        }
        return result;
    }

    private boolean star(final byte[] candidate, final int pos, final int end, final int index) {
        boolean result = index == this.tail;
        for (int start = pos; !result && start <= end; ++start) {
            result = this.matches(candidate, start, end, index);
        }
        return result;
    }

    private int width(final byte[] candidate, final int pos, final int end, final int index) {
        int result = 0;
        if (pos < end) {
            result = this.step(candidate, pos, end, index);
        }
        return result;
    }

    private int step(final byte[] candidate, final int pos, final int end, final int index) {
        int result = 0;
        if (this.features.single() && this.pattern[index] == '?') {
            result = Glob.length(candidate[pos]);
            if (end - pos < result) {
                result = 0;
            }
        } else if (this.pattern[index] == candidate[pos]) {
            result = 1;
        }
        return result;
    }

    private static GlobBounds measure(
        final byte[] bytes, final WildcardPattern glob, final int head, final int tail
    ) {
        long low = head;
        long high = head;
        int marks = 0;
        boolean star = false;
        long gate = Glob.UNBOUNDED;
        for (int index = head; index != bytes.length; ++index) {
            final byte chr = bytes[index];
            final boolean special = Glob.special(glob, chr);
            if (special && chr == '*') {
                star = true;
            } else if (special) {
                marks = marks + 1;
                low = low + 1L;
                high = high + 4L;
            } else {
                low = low + 1L;
                high = high + 1L;
            }
            if (index < tail - 1 && !Glob.special(glob, chr)) {
                gate = 0L;
            }
        }
        if (star) {
            high = Glob.UNBOUNDED;
        } else if (marks > 1) {
            gate = 0L;
        } else if (marks == 1) {
            gate = low + 1L;
        }
        return new GlobBounds(low, high, gate);
    }

    private static int length(final byte lead) {
        final int value = lead & 0xFF;
        int result = 1;
        if (value >= 0xF0) {
            result = 4;
        } else if (value >= 0xE0) {
            result = 3;
        } else if (value >= 0xC0) {
            result = 2;
        }
        return result;
    }

    private static boolean special(final WildcardPattern glob, final byte chr) {
        return glob.multi() && chr == '*' || glob.single() && chr == '?';
    }
}
