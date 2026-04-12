/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.charset.StandardCharsets;

/**
 * Static estimates of the documents a value range matches, assuming values
 * spread evenly between the bounds of the slot.
 *
 * @since 0.3.0
 */
final class RangeEstimate {

    private RangeEstimate() {
    }

    static MatchEstimates of(final ValueStats stats, final String begin, final String end) {
        final String lower = stats.lower();
        final String upper = stats.upper();
        final long freq = stats.frequency().value();
        final MatchEstimates result;
        if (lower.isEmpty() || !end.isEmpty() && end.compareTo(lower) < 0
            || begin.compareTo(upper) > 0) {
            result = RangeEstimate.values(0L, 0L, 0L);
        } else if ((end.isEmpty() || end.compareTo(upper) >= 0)
            && begin.compareTo(lower) <= 0) {
            result = RangeEstimate.values(freq, freq, freq);
        } else {
            result = RangeEstimate.values(
                0L, RangeEstimate.frequency(stats, begin, end), freq
            );
        }
        return result;
    }

    static long frequency(
        final ValueStats stats, final String begin, final String end
    ) {
        final String low = stats.lower();
        final String high = stats.upper();
        final long freq = stats.frequency().value();
        final byte[] lows = RangeEstimate.bytes(low);
        final byte[] highs = RangeEstimate.bytes(high);
        int common = 0;
        while (common < lows.length && common < highs.length
            && lows[common] == highs[common]) {
            common = common + 1;
        }
        final boolean same = common == lows.length && common == highs.length;
        final long result;
        final double lfrac = RangeEstimate.fraction(lows, common);
        final double hfrac = RangeEstimate.fraction(highs, common);
        final double denom = hfrac - lfrac;
        if (same) {
            result = freq;
        } else if (denom == 0.0) {
            result = freq / 2L;
        } else {
            double from = lfrac;
            if (begin.compareTo(low) > 0) {
                from = RangeEstimate.fraction(RangeEstimate.bytes(begin), common);
            }
            double upto = hfrac;
            if (!end.isEmpty() && end.compareTo(high) < 0) {
                upto = RangeEstimate.fraction(RangeEstimate.bytes(end), common);
            }
            result = (long) ((upto - from) / denom * freq + 0.5);
        }
        return result;
    }

    private static double fraction(final byte[] text, final int prefix) {
        double result = 0.0;
        double unit = 1.0;
        for (int idx = prefix; idx < text.length; idx = idx + 1) {
            unit /= 256.0;
            result += (text[idx] & 0xFF) * unit;
        }
        return result;
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static MatchEstimates values(final long min, final long est, final long max) {
        return new MatchEstimates(
            new TermCount((int) min), new TermCount((int) est),
            new TermCount((int) max), DocId.of(1), new DocId(Integer.MAX_VALUE)
        );
    }
}
