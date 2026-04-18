/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.unicode.Utf8;
import java.nio.charset.StandardCharsets;

/**
 * Bounded edit distance between a target and candidate terms, counting
 * insertions, deletions, substitutions and transpositions of adjacent
 * characters, by the diagonal method of Berghel and Roach.
 *
 * @since 0.10.0
 */
final class EditCalculator {

    /**
     * Mask of a character into the occurrence bitmaps.
     */
    private static final int BITS = 63;

    /**
     * Target characters.
     */
    private final int[] target;

    /**
     * Length of the target in bytes.
     */
    private final int bytes;

    /**
     * Characters occurring in the target.
     */
    private final long once;

    /**
     * Characters occurring twice in the target.
     */
    private final long twice;

    private EditCalculator(
        final int[] target, final int bytes, final long once, final long twice
    ) {
        this.target = target;
        this.bytes = bytes;
        this.once = once;
        this.twice = twice;
    }

    static EditCalculator of(final String pattern) {
        final byte[] raw = pattern.getBytes(StandardCharsets.UTF_8);
        final int[] chars = Utf8.codepoints(raw);
        return new EditCalculator(
            chars, raw.length, EditCalculator.first(chars), EditCalculator.second(chars)
        );
    }

    int distance(final byte[] candidate, final int max) {
        int result = Integer.MAX_VALUE;
        final long size = candidate.length;
        if (this.target.length <= size + max && this.bytes <= size + 4L * max
            && this.bytes + 4L * max >= size) {
            final int[] chars = Utf8.codepoints(candidate);
            final int lower = Math.abs(chars.length - this.target.length);
            if (lower > max) {
                result = lower;
            } else {
                result = this.calc(chars, max);
            }
        }
        return result;
    }

    private int calc(final int[] chars, final int max) {
        final int bits = 1 + Long.bitCount(EditCalculator.first(chars) ^ this.once)
            + Long.bitCount(EditCalculator.second(chars) ^ this.twice);
        int result = bits / 2;
        if (result <= max) {
            result = EditCalculator.sequence(chars, this.target, max);
        }
        return result;
    }

    private static int sequence(final int[] left, final int[] right, final int max) {
        int result = Math.max(left.length, right.length);
        if (left.length != 0 && right.length != 0) {
            final Diagonals state;
            if (right.length < left.length) {
                state = Diagonals.of(right, left);
            } else {
                state = Diagonals.of(left, right);
            }
            result = state.distance(max);
        }
        return result;
    }

    private static long first(final int... chars) {
        long seen = 0L;
        for (final int chr : chars) {
            seen |= 1L << (chr & EditCalculator.BITS);
        }
        return seen;
    }

    private static long second(final int... chars) {
        long seen = 0L;
        long again = 0L;
        for (final int chr : chars) {
            final long bit = 1L << (chr & EditCalculator.BITS);
            again |= seen & bit;
            seen |= bit;
        }
        return again;
    }
}
