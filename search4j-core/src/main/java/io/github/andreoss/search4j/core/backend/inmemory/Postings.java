/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory database backend of the search engine.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.NavigableMap;

/**
 * Postings of a term as opened: a cursor, the frequencies at that time and
 * the bound on the within-document frequency.
 *
 * @param cursor Cursor over the postings
 * @param freqs Frequencies when opened
 * @param bound Larger of the first frequency and the rest of the collection
 *  frequency
 * @param range First and last documents listed
 * @since 0.1.0
 */
record Postings(
    Seek<DocId, Occurrence> cursor, TermFrequencies freqs, TermCount bound,
    DocRange range
) {

    /**
     * Opens the postings of a term.
     *
     * @param store Store holding the documents
     * @param term Non-empty term listed
     * @return The postings
     */
    static Postings open(final Store store, final String term) {
        final NavigableMap<DocId, Occurrence> list = store.postings(term);
        final TermCount coll = store.collection(term);
        TermCount bound = TermCount.ZERO;
        DocRange range = new DocRange(DocId.of(1), DocId.NONE);
        if (!list.isEmpty()) {
            range = new DocRange(list.firstKey(), list.lastKey());
            final int head = list.firstEntry().getValue().wdf().value();
            bound = new TermCount(Math.max(head, coll.value() - head));
        }
        return new Postings(
            new Seek<>(list, DocId.NONE, item -> true),
            new TermFrequencies(store.frequency(term), coll),
            bound,
            range
        );
    }
}
