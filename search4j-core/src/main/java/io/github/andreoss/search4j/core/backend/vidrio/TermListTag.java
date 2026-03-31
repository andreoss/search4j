/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.TermCount;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Encoder of a term list tag as the reference lays it out.
 *
 * <p>An empty list is an empty tag. Otherwise the document length and the
 * term count come first as packed integers, then the terms in byte order:
 * the first as its length byte, its bytes and its packed frequency; each
 * later one as a reuse byte, the length of the bytes added, those bytes
 * and, unless the reuse byte carries it, its packed frequency. The reuse
 * byte holds the shared prefix length alone, or, when the frequency is
 * below 127 and the product fits, one above the frequency times one above
 * the previous term's length, plus the prefix length.</p>
 *
 * @since 0.1.0
 */
final class TermListTag {

    /**
     * Number of values of one byte.
     */
    private static final int BYTE = 256;

    /**
     * Utility class, no instances.
     */
    private TermListTag() {
    }

    /**
     * Encodes a term list.
     *
     * @param terms Terms of one to 255 bytes with their frequency, in any
     *  order
     * @return Tag bytes
     */
    static byte[] encode(final Map<String, TermCount> terms) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!terms.isEmpty()) {
            final NavigableMap<String, TermCount> sorted =
                new TreeMap<>(TermOrder.INSTANCE);
            sorted.putAll(terms);
            out.writeBytes(Varints.pack(TermListTag.length(sorted).value()));
            out.writeBytes(Varints.pack(sorted.size()));
            byte[] prev = new byte[0];
            for (final Map.Entry<String, TermCount> entry : sorted.entrySet()) {
                final byte[] term = TermListTag.bytes(entry.getKey());
                TermListTag.append(out, prev, term, entry.getValue().value());
                prev = term;
            }
        }
        return out.toByteArray();
    }

    private static void append(
        final ByteArrayOutputStream out, final byte[] prev, final byte[] term,
        final int wdf
    ) {
        if (prev.length == 0) {
            out.write(term.length);
            out.writeBytes(term);
            out.writeBytes(Varints.pack(wdf));
        } else {
            final int reuse = TermListTag.shared(prev, term);
            int packed = 0;
            if (wdf < 127) {
                packed = (wdf + 1) * (prev.length + 1) + reuse;
            }
            final boolean fits = packed > 0 && packed < TermListTag.BYTE;
            if (fits) {
                out.write(packed);
            } else {
                out.write(reuse);
            }
            out.write(term.length - reuse);
            out.write(term, reuse, term.length - reuse);
            if (!fits) {
                out.writeBytes(Varints.pack(wdf));
            }
        }
    }

    private static byte[] bytes(final String term) {
        final byte[] bytes = term.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            throw new InvalidArgumentError("Empty term in a term list");
        }
        if (bytes.length >= TermListTag.BYTE) {
            throw new InvalidArgumentError(
                String.format(
                    "Term too long for a term list: %d bytes", bytes.length
                )
            );
        }
        return bytes;
    }

    private static TermCount length(final Map<String, TermCount> terms) {
        TermCount total = TermCount.ZERO;
        for (final TermCount wdf : terms.values()) {
            total = total.plus(wdf);
        }
        return total;
    }

    private static int shared(final byte[] prev, final byte[] term) {
        final int limit = Math.min(prev.length, term.length);
        int index = 0;
        while (index < limit && prev[index] == term[index]) {
            index += 1;
        }
        return index;
    }
}
