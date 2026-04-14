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
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Changes to the values of documents, kept by slot and merged into the
 * chunks on {@link #merge}.
 *
 * <p>Reads see the changes pending before the tables.</p>
 *
 * @since 0.1.0
 */
public final class ValueWriter {

    /**
     * Posting list table holding the chunks and statistics.
     */
    private final TableWriter postings;

    /**
     * Term list table holding the slots used.
     */
    private final TableWriter termlists;

    /**
     * Values changed, by slot and document; empty for a value removed.
     */
    private final NavigableMap<ValueNo, NavigableMap<DocId, String>> changes;

    /**
     * Slots used by the documents changed, encoded, in document order;
     * empty for none.
     */
    private final Map<DocId, byte[]> slots;

    /**
     * Statistics of the slots changed.
     */
    private final PendingStats stats;

    /**
     * Creates the changes over the tables.
     *
     * @param postings Posting list table holding the chunks and statistics
     * @param termlists Term list table holding the slots used
     */
    public ValueWriter(final TableWriter postings, final TableWriter termlists) {
        this.postings = postings;
        this.termlists = termlists;
        this.changes = new TreeMap<>();
        this.slots = new TreeMap<>();
        this.stats = new PendingStats(
            slot -> ValueTable.of(postings, termlists).stats(slot)
        );
    }

    /**
     * Adds the values of a document without any.
     *
     * @param doc Document identifier
     * @param values Values by slot, none empty
     */
    public void add(final DocId doc, final SortedMap<ValueNo, String> values) {
        for (final Map.Entry<ValueNo, String> entry : values.entrySet()) {
            if (entry.getValue().isEmpty()) {
                throw new InvalidArgumentError(
                    String.format("Empty value in slot %s", entry.getKey())
                );
            }
            this.stats.added(entry.getKey(), entry.getValue());
            this.change(entry.getKey()).put(doc, entry.getValue());
        }
        final byte[] used = SlotsTag.encode(values.keySet());
        if (used.length > 0 || this.slots.containsKey(doc)) {
            this.slots.put(doc, used);
        }
    }

    /**
     * Removes the values of a document.
     *
     * @param doc Document identifier
     */
    public void delete(final DocId doc) {
        final Optional<byte[]> used = this.used(doc);
        if (used.isPresent()) {
            this.slots.put(doc, new byte[0]);
            for (final ValueNo slot : SlotsTag.decode(used.get())) {
                this.stats.removed(slot);
                this.change(slot).put(doc, "");
            }
        }
    }

    /**
     * Replaces the values of a document.
     *
     * @param doc Document identifier
     * @param values Values by slot, none empty
     */
    public void replace(final DocId doc, final SortedMap<ValueNo, String> values) {
        this.delete(doc);
        this.add(doc, values);
    }

    /**
     * Value of a document in a slot as changed so far.
     *
     * @param doc Document identifier
     * @param slot Slot number
     * @return Value, empty when the document has none in the slot
     */
    public String value(final DocId doc, final ValueNo slot) {
        String result = "";
        boolean stored = true;
        final NavigableMap<DocId, String> pending = this.changes.get(slot);
        if (pending != null) {
            final String changed = pending.get(doc);
            if (changed != null) {
                result = changed;
                stored = false;
            }
        }
        if (stored) {
            result = this.stored().value(doc, slot);
        }
        return result;
    }

    /**
     * Every value of a document as changed so far.
     *
     * @param doc Document identifier
     * @return Values by slot, empty when the document has none
     */
    public NavigableMap<ValueNo, String> values(final DocId doc) {
        final NavigableMap<ValueNo, String> result = new TreeMap<>();
        final byte[] used = this.used(doc).orElseGet(() -> new byte[0]);
        for (final ValueNo slot : SlotsTag.decode(used)) {
            result.put(slot, this.value(doc, slot));
        }
        return result;
    }

    /**
     * Statistics of a slot as changed so far.
     *
     * @param slot Slot number
     * @return Statistics, {@link ValueStats#NONE} for an unused slot
     */
    public ValueStats stats(final ValueNo slot) {
        return this.stats.get(slot).orElseGet(() -> this.stored().stats(slot));
    }

    /**
     * Tests whether changes are pending.
     *
     * @return True when a value was added or removed since the last merge
     */
    public boolean modified() {
        return !this.changes.isEmpty();
    }

    /**
     * Writes the statistics of the slots changed and forgets them; done
     * only when a revision is committed, before {@link #merge()}.
     */
    public void writeStats() {
        this.stats.write(this.postings);
    }

    /**
     * Merges the value changes into the tables and forgets them: the slots
     * used by each document, then the value chunks. The statistics stay
     * pending until {@link #writeStats()}.
     */
    public void merge() {
        for (final Map.Entry<DocId, byte[]> entry : this.slots.entrySet()) {
            final byte[] key = ValueKeys.slots(entry.getKey());
            if (entry.getValue().length == 0) {
                this.termlists.delete(key);
            } else {
                this.termlists.add(key, entry.getValue());
            }
        }
        this.slots.clear();
        for (final Map.Entry<ValueNo, NavigableMap<DocId, String>> entry
            : this.changes.entrySet()) {
            final ValueChunkWriter writer =
                ValueChunkWriter.over(this.postings, entry.getKey());
            for (final Map.Entry<DocId, String> change : entry.getValue().entrySet()) {
                writer.update(change.getKey(), change.getValue());
            }
            writer.finish();
        }
        this.changes.clear();
    }

    /**
     * Forgets the changes.
     */
    public void cancel() {
        this.slots.clear();
        this.changes.clear();
        this.stats.clear();
    }

    private Optional<byte[]> used(final DocId doc) {
        Optional<byte[]> result = Optional.ofNullable(this.slots.get(doc));
        if (result.isEmpty()) {
            result = this.termlists.get(ValueKeys.slots(doc));
        }
        return result;
    }

    private NavigableMap<DocId, String> change(final ValueNo slot) {
        return this.changes.computeIfAbsent(slot, key -> new TreeMap<>());
    }

    private ValueTable stored() {
        return ValueTable.of(this.postings, this.termlists);
    }
}
