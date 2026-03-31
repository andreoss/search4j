/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.error.RangeError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * Indexes documents into a session: data and term lists go to their
 * tables at once, postings, lengths and positions to the inverter, values
 * to the value changes, counts and bounds to the version file.
 *
 * @since 0.1.0
 */
public final class Indexer {

    /**
     * The session written.
     */
    private final WriteSession session;

    /**
     * Read side of the session.
     */
    private final WritableReads reads;

    /**
     * Creates the indexer of a session.
     *
     * @param session The session written
     * @param reads Read side of the session
     */
    public Indexer(final WriteSession session, final WritableReads reads) {
        this.session = session;
        this.reads = reads;
    }

    /**
     * Takes the next identifier, raising the greatest one used.
     *
     * @return Identifier for a document added
     */
    public DocId next() {
        final long last = this.session.version().stats().last();
        if (last >= Integer.MAX_VALUE) {
            throw new RangeError(
                "Run out of docids - you'll have to use copydatabase to eliminate any gaps before you can add more documents"
            );
        }
        final DocId doc = new DocId((int) last + 1);
        this.stats(stats -> stats.withLast(doc.value()));
        return doc;
    }

    /**
     * Adds a document under an identifier not in use.
     *
     * @param doc Identifier
     * @param document Document indexed
     */
    public void add(final DocId doc, final Document document) {
        this.data(doc, document.data());
        this.session.values().add(doc, Indexer.values(document));
        final NavigableMap<String, TermOccurrence> terms =
            TermOccurrence.of(document);
        TermCount length = TermCount.ZERO;
        for (final Map.Entry<String, TermOccurrence> entry : terms.entrySet()) {
            length = length.plus(this.added(doc, entry.getKey(), entry.getValue()));
        }
        this.termlist(doc, terms);
        final TermCount total = length;
        this.session.inverter().lengths().set(doc, total, true);
        this.stats(stats -> stats.added(total.value()));
    }

    /**
     * Deletes a document known to be present.
     *
     * @param doc Identifier
     */
    public void delete(final DocId doc) {
        this.session.tables().opened(TableType.DOCDATA)
            .ifPresent(table -> table.delete(DocDataKeys.of(doc).data()));
        this.session.values().delete(doc);
        final TermCount length = this.reads.docLength(doc);
        TermList list = this.reads.terms(doc);
        this.stats(stats -> stats.deleted(length.value()));
        list = list.next();
        while (!list.atEnd()) {
            this.removed(doc, list.term(), list.wdf());
            list = list.next();
        }
        new TermListWriter(this.session.tables().writer(TableType.TERMLIST))
            .delete(doc);
        this.session.inverter().lengths().delete(doc);
    }

    /**
     * Stores a document at an identifier, adding it when absent.
     *
     * @param doc Identifier
     * @param document Document indexed
     */
    public void replace(final DocId doc, final Document document) {
        if (doc.value() > this.session.version().stats().last()) {
            this.stats(stats -> stats.withLast(doc.value()));
            this.add(doc, document);
        } else if (this.session.lookup(TableType.TERMLIST)
            .contains(TermListKeys.of(doc))) {
            this.merge(doc, document);
            this.data(doc, document.data());
            this.session.values().replace(doc, Indexer.values(document));
        } else {
            this.add(doc, document);
        }
    }

    private void merge(final DocId doc, final Document document) {
        final NavigableMap<String, TermOccurrence> fresh =
            TermOccurrence.of(document);
        final TermCount before = this.reads.docLength(doc);
        TermList old = this.reads.terms(doc);
        this.stats(stats -> stats.deleted(before.value()));
        long length = before.value();
        old = old.next();
        final Iterator<Map.Entry<String, TermOccurrence>> iter =
            fresh.entrySet().iterator();
        Map.Entry<String, TermOccurrence> entry = Indexer.step(iter);
        while (!old.atEnd() || entry != null) {
            final int cmp;
            if (old.atEnd()) {
                cmp = 1;
            } else if (entry == null) {
                cmp = -1;
            } else {
                cmp = TermOrder.INSTANCE.compare(old.term(), entry.getKey());
            }
            if (cmp < 0) {
                length -= this.removed(doc, old.term(), old.wdf()).value();
                old = old.next();
            } else if (cmp > 0) {
                length += this.added(doc, entry.getKey(), entry.getValue()).value();
                entry = Indexer.step(iter);
            } else {
                length += this.kept(doc, old, entry.getValue());
                old = old.next();
                entry = Indexer.step(iter);
            }
        }
        this.termlist(doc, fresh);
        final TermCount after = Indexer.length(length);
        if (!after.equals(before)) {
            this.session.inverter().lengths().set(doc, after, false);
        }
        this.stats(stats -> stats.added(after.value()));
    }

    private TermCount added(
        final DocId doc, final String term, final TermOccurrence occurrence
    ) {
        this.stats(stats -> stats.checked(occurrence.wdf().value()));
        this.session.inverter().addPosting(doc, term, occurrence.wdf());
        if (!occurrence.positions().isEmpty()) {
            this.session.inverter().positions()
                .set(doc, term, occurrence.positions());
        }
        return occurrence.wdf();
    }

    private TermCount removed(
        final DocId doc, final String term, final TermCount wdf
    ) {
        this.session.inverter().positions().delete(doc, term);
        this.session.inverter().removePosting(doc, term, wdf);
        return wdf;
    }

    private long kept(
        final DocId doc, final TermList old, final TermOccurrence occurrence
    ) {
        this.stats(stats -> stats.checked(occurrence.wdf().value()));
        long delta = 0L;
        if (!old.wdf().equals(occurrence.wdf())) {
            delta = (long) occurrence.wdf().value() - old.wdf().value();
            this.session.inverter().updatePosting(
                doc, old.term(), new WdfChange(old.wdf(), occurrence.wdf())
            );
        }
        this.session.inverter().positions().store(
            this.session.lookup(TableType.POSITION),
            new PositionKey(doc, old.term()), occurrence.positions()
        );
        return delta;
    }

    private void termlist(
        final DocId doc, final Map<String, TermOccurrence> terms
    ) {
        new TermListWriter(this.session.tables().writer(TableType.TERMLIST))
            .set(doc, TermOccurrence.wdfs(terms));
    }

    private void data(final DocId doc, final String data) {
        final byte[] key = DocDataKeys.of(doc).data();
        if (data.isEmpty()) {
            this.session.tables().opened(TableType.DOCDATA)
                .ifPresent(table -> table.delete(key));
        } else {
            this.session.tables().writer(TableType.DOCDATA)
                .add(key, data.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void stats(final UnaryOperator<DatabaseStats> change) {
        this.session.version().stats(
            change.apply(this.session.version().stats())
        );
    }

    private static SortedMap<ValueNo, String> values(final Document document) {
        final SortedMap<ValueNo, String> result = new TreeMap<>();
        try (ValueIterator iter = document.values()) {
            while (iter.hasNext()) {
                final String value = iter.next();
                result.put(iter.slot(), value);
            }
        }
        return result;
    }

    private static Map.Entry<String, TermOccurrence> step(
        final Iterator<Map.Entry<String, TermOccurrence>> iter
    ) {
        Map.Entry<String, TermOccurrence> result = null;
        if (iter.hasNext()) {
            result = iter.next();
        }
        return result;
    }

    private static TermCount length(final long value) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new RangeError(
                String.format("Document length out of range: %d", value)
            );
        }
        return new TermCount((int) value);
    }
}
