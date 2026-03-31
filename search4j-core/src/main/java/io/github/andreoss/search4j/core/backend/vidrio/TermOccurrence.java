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

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A term of a document being indexed: its frequency and positions.
 *
 * @param wdf Frequency in the document
 * @param positions Ascending positions, none when the term has no position
 * @since 0.1.0
 */
public record TermOccurrence(TermCount wdf, List<TermPos> positions) {

    /**
     * Creates the occurrence over a copy of the positions.
     *
     * @param wdf Frequency in the document
     * @param positions Ascending positions
     */
    public TermOccurrence {
        positions = List.copyOf(positions);
    }

    /**
     * Terms of a document in term order.
     *
     * <p>A term longer than the format stores raises an invalid-argument
     * error naming the limit.</p>
     *
     * @param document Document indexed
     * @return Occurrences by term
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static NavigableMap<String, TermOccurrence> of(final Document document) {
        final NavigableMap<String, TermOccurrence> result =
            new TreeMap<>(TermOrder.INSTANCE);
        try (TermIterator terms = document.termList()) {
            while (terms.hasNext()) {
                final String term = terms.next();
                if (PostListKeys.bytes(term).length > VidrioFormat.MAX_TERM_LENGTH) {
                    throw new InvalidArgumentError(
                        String.format(
                            "Term too long (> %d): %s",
                            VidrioFormat.MAX_TERM_LENGTH, term
                        )
                    );
                }
                final List<TermPos> positions = new ArrayList<>(0);
                try (PositionIterator iter = terms.positions()) {
                    while (iter.hasNext()) {
                        positions.add(iter.next());
                    }
                }
                result.put(term, new TermOccurrence(terms.wdf(), positions));
            }
        }
        return result;
    }

    /**
     * Frequencies of the terms, as the term list stores them.
     *
     * @param terms Occurrences by term
     * @return Frequency by term
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static Map<String, TermCount> wdfs(
        final Map<String, TermOccurrence> terms
    ) {
        final Map<String, TermCount> result = new TreeMap<>(TermOrder.INSTANCE);
        for (final Map.Entry<String, TermOccurrence> entry : terms.entrySet()) {
            result.put(entry.getKey(), entry.getValue().wdf());
        }
        return result;
    }
}
