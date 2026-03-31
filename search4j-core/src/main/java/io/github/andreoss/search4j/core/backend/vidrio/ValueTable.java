/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Lookups of the values stored: the chunks and statistics in a posting
 * list table, the slots each document uses in a term list table.
 *
 * @since 0.1.0
 */
public final class ValueTable {

    /**
     * Posting list table holding the chunks and statistics.
     */
    private final TableLookup postings;

    /**
     * Term list table holding the slots used.
     */
    private final TableLookup termlists;

    /**
     * Creates the lookups from their parts.
     *
     * @param postings Posting list table holding the chunks and statistics
     * @param termlists Term list table holding the slots used
     */
    private ValueTable(final TableLookup postings, final TableLookup termlists) {
        this.postings = postings;
        this.termlists = termlists;
    }

    /**
     * Opens the lookups over the tables.
     *
     * @param postings Posting list table holding the chunks and statistics
     * @param termlists Term list table holding the slots used
     * @return Lookups
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static ValueTable of(
        final TableLookup postings, final TableLookup termlists
    ) {
        return new ValueTable(postings, termlists);
    }

    /**
     * Value of a document in a slot.
     *
     * @param doc Document
     * @param slot Slot
     * @return Value, empty when the document has none in the slot
     */
    public String value(final DocId doc, final ValueNo slot) {
        String result = "";
        final Optional<ValueChunk> chunk = this.chunk(slot, doc);
        if (chunk.isPresent()) {
            final ValueChunk reader = chunk.get();
            reader.skipTo(doc);
            if (!reader.atEnd() && reader.docId().equals(doc)) {
                result = reader.value();
            }
        }
        return result;
    }

    /**
     * Every value of a document.
     *
     * @param doc Document
     * @return Values by slot, empty when the document has none
     */
    public NavigableMap<ValueNo, String> values(final DocId doc) {
        final NavigableMap<ValueNo, String> result = new TreeMap<>();
        for (final ValueNo slot : this.slots(doc)) {
            result.put(slot, this.value(doc, slot));
        }
        return result;
    }

    /**
     * Slots a document uses.
     *
     * @param doc Document
     * @return Slots in ascending order, empty when the document has none
     */
    public List<ValueNo> slots(final DocId doc) {
        return this.termlists.get(ValueKeys.slots(doc))
            .map(SlotsTag::decode).orElseGet(List::of);
    }

    /**
     * Statistics of a slot.
     *
     * @param slot Slot
     * @return Statistics, {@link ValueStats#NONE} for an unused slot
     */
    public ValueStats stats(final ValueNo slot) {
        return this.postings.get(ValueKeys.stats(slot))
            .map(ValueStatsTag::decode).orElse(ValueStats.NONE);
    }

    /**
     * Opens the values of a slot across documents.
     *
     * @param slot Slot
     * @return List before its first value
     */
    public VidrioValueList open(final ValueNo slot) {
        return VidrioValueList.of(this.postings, slot);
    }

    /**
     * Opens the chunk that may hold a document.
     *
     * @param slot Slot
     * @param doc Document
     * @return Chunk on its first document, or empty when no chunk of the
     *  slot starts at or before the document
     */
    Optional<ValueChunk> chunk(final ValueNo slot, final DocId doc) {
        final TableCursor cursor = this.postings.cursor();
        final Optional<DocId> first;
        if (cursor.seek(ValueKeys.chunk(slot, doc))) {
            first = Optional.of(doc);
        } else {
            first = ValueKeys.chunkStart(slot, cursor.key());
        }
        return first.map(start -> ValueChunk.of(cursor.tag(), start));
    }
}
