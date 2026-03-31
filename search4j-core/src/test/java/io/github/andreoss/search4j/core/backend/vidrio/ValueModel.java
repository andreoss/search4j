/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Random documents with values in a few slots, changed through a writer
 * and checked against the values read back.
 *
 * @since 0.1.0
 */
final class ValueModel {

    /**
     * Slots used by the documents.
     */
    private static final List<ValueNo> SLOTS = List.of(
        new ValueNo(0L), new ValueNo(1L), new ValueNo(2L), new ValueNo(7L),
        new ValueNo(300L), new ValueNo(70_000L)
    );

    /**
     * Highest document number used.
     */
    private static final int DOCS = 250;

    /**
     * Source of the changes.
     */
    private final Random random;

    /**
     * Values of each document.
     */
    private final NavigableMap<DocId, SortedMap<ValueNo, String>> docs;

    /**
     * Creates an empty model.
     *
     * @param seed Seed of the changes
     */
    ValueModel(final long seed) {
        this.random = new Random(seed);
        this.docs = new TreeMap<>();
    }

    /**
     * Adds, replaces or deletes a random document.
     *
     * @param writer Writer changed alike
     */
    void change(final ValueWriter writer) {
        final DocId doc = DocId.of(1 + this.random.nextInt(ValueModel.DOCS));
        if (this.docs.containsKey(doc) && this.random.nextInt(5) < 2) {
            writer.delete(doc);
            this.docs.remove(doc);
        } else {
            final SortedMap<ValueNo, String> values = new TreeMap<>();
            for (final ValueNo slot : ValueModel.SLOTS) {
                if (this.random.nextBoolean()) {
                    values.put(slot, this.text(slot));
                }
            }
            if (this.docs.containsKey(doc)) {
                writer.replace(doc, values);
            } else {
                writer.add(doc, values);
            }
            this.docs.put(doc, values);
        }
    }

    /**
     * Compares the values pending in a writer with the model.
     *
     * @param writer Writer read
     * @return Mismatches
     */
    List<String> check(final ValueWriter writer) {
        return this.check((doc, slot) -> writer.values(doc), writer::value);
    }

    /**
     * Compares the values of a table with the model, by document and by
     * slot with skips.
     *
     * @param table Table read
     * @return Mismatches
     */
    List<String> check(final ValueTable table) {
        final List<String> failures =
            this.check((doc, slot) -> table.values(doc), table::value);
        for (final ValueNo slot : ValueModel.SLOTS) {
            final NavigableMap<DocId, String> expected = this.column(slot);
            if (!ValueModel.entries(expected).equals(ValueModel.read(table.open(slot)))) {
                failures.add(String.format("list of slot %s", slot));
            }
            failures.addAll(this.skips(table.open(slot), expected));
            if (!ValueModel.plausible(table.stats(slot), expected)) {
                failures.add(String.format("stats of %s", slot));
            }
        }
        return failures;
    }

    private List<String> check(
        final Lookup<SortedMap<ValueNo, String>> values, final Lookup<String> value
    ) {
        final List<String> failures = new ArrayList<>(0);
        for (int number = 1; number <= ValueModel.DOCS; ++number) {
            final DocId doc = DocId.of(number);
            final SortedMap<ValueNo, String> expected =
                this.docs.getOrDefault(doc, new TreeMap<>());
            if (!expected.equals(values.read(doc, ValueModel.SLOTS.get(0)))) {
                failures.add(String.format("values of %s", doc));
            }
            for (final ValueNo slot : ValueModel.SLOTS) {
                if (!expected.getOrDefault(slot, "").equals(value.read(doc, slot))) {
                    failures.add(String.format("value of %s in %s", doc, slot));
                }
            }
        }
        return failures;
    }

    private List<String> skips(
        final VidrioValueList list, final NavigableMap<DocId, String> expected
    ) {
        final List<String> failures = new ArrayList<>(0);
        int target = 0;
        while (!list.atEnd()) {
            target += 1 + this.random.nextInt(12);
            final DocId wanted = DocId.of(target);
            list.skipTo(wanted);
            final DocId next = expected.ceilingKey(wanted);
            final boolean good;
            if (next == null) {
                good = list.atEnd();
            } else {
                good = !list.atEnd() && list.docId().equals(next)
                    && list.value().equals(expected.get(next));
            }
            if (!good) {
                failures.add(String.format("skip to %s in %s", wanted, list.slot()));
            }
        }
        return failures;
    }

    private String text(final ValueNo slot) {
        int length = 1 + this.random.nextInt(30);
        if (slot.value() == 7L) {
            length += 100;
        }
        final StringBuilder out = new StringBuilder(length);
        for (int idx = 0; idx < length; ++idx) {
            out.appendCodePoint('a' + this.random.nextInt(26));
        }
        if (this.random.nextInt(10) == 0) {
            out.appendCodePoint(0x1F600);
        }
        return out.toString();
    }

    private NavigableMap<DocId, String> column(final ValueNo slot) {
        final NavigableMap<DocId, String> result = new TreeMap<>();
        for (final Map.Entry<DocId, SortedMap<ValueNo, String>> doc
            : this.docs.entrySet()) {
            final String value = doc.getValue().get(slot);
            if (value != null) {
                result.put(doc.getKey(), value);
            }
        }
        return result;
    }

    private static boolean plausible(
        final ValueStats stats, final NavigableMap<DocId, String> column
    ) {
        final boolean result;
        if (column.isEmpty()) {
            result = stats.equals(ValueStats.NONE);
        } else {
            final List<String> sorted = new ArrayList<>(column.values());
            sorted.sort(TermOrder.INSTANCE);
            result = stats.frequency().value() == column.size()
                && TermOrder.INSTANCE.compare(stats.lower(), sorted.get(0)) <= 0
                && TermOrder.INSTANCE.compare(
                    stats.upper(), sorted.get(sorted.size() - 1)
                ) >= 0;
        }
        return result;
    }

    private static List<String> entries(final NavigableMap<DocId, String> column) {
        final List<String> result = new ArrayList<>(column.size());
        for (final Map.Entry<DocId, String> entry : column.entrySet()) {
            result.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static List<String> read(final VidrioValueList list) {
        final List<String> result = new ArrayList<>(0);
        list.next();
        while (!list.atEnd()) {
            result.add(String.format("%s=%s", list.docId(), list.value()));
            list.next();
        }
        return result;
    }

    /**
     * A read of a document and slot.
     *
     * @param <T> Type read
     * @since 0.1.0
     */
    @FunctionalInterface
    private interface Lookup<T> {

        /**
         * Reads a document and slot.
         *
         * @param doc Document
         * @param slot Slot
         * @return Value read
         */
        T read(DocId doc, ValueNo slot);
    }
}
