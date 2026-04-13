/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Encoding of the statistics of a slot: the packed frequency, the
 * length-prefixed lower bound and the upper bound as the rest, left out
 * when equal to the lower bound.
 *
 * @since 0.1.0
 */
final class ValueStatsTag {

    /**
     * Utility class, no instances.
     */
    private ValueStatsTag() {
    }

    /**
     * Encodes the statistics of a used slot.
     *
     * @param stats Statistics with a frequency above zero
     * @return Tag bytes
     */
    static byte[] encode(final ValueStats stats) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(Varints.pack(stats.frequency().value()));
        final byte[] lower = stats.lower().getBytes(StandardCharsets.UTF_8);
        out.writeBytes(Varints.pack(lower.length));
        out.writeBytes(lower);
        if (!stats.lower().equals(stats.upper())) {
            out.writeBytes(stats.upper().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    /**
     * Decodes the statistics of a slot.
     *
     * @param tag Tag bytes
     * @return Decoded statistics
     */
    static ValueStats decode(final byte[] tag) {
        final ByteReader reader = ByteReader.over(tag);
        final long freq = reader.narrow("Value frequency");
        String lower = new String(
            reader.string("Value lower bound"), StandardCharsets.UTF_8
        );
        if (lower.isEmpty() && freq != 0L) {
            lower = Character.toString(0);
        }
        final byte[] rest = reader.rest();
        final String upper;
        if (rest.length == 0) {
            upper = lower;
        } else {
            upper = new String(rest, StandardCharsets.UTF_8);
        }
        return new ValueStats(new TermCount(freq), lower, upper);
    }
}
