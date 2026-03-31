/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything a database reports through the public API, as lines a test
 * compares between backends.
 *
 * @since 0.1.0
 */
final class Snapshot {

    /**
     * Utility class, no instances.
     */
    private Snapshot() {
    }

    /**
     * Reads a database into lines.
     *
     * @param database Database read
     * @param terms Terms listed
     * @param docs Highest document identifier listed
     * @return Lines in a fixed order
     */
    static List<String> of(
        final Database database, final List<String> terms, final int docs
    ) {
        final List<String> lines = new ArrayList<>(0);
        lines.add(
            String.format(
                "stats %s %s %s %s", database.docCount(), database.lastDocId(),
                database.totalLength(), database.hasPositions()
            )
        );
        for (final String term : terms) {
            Snapshot.term(database, term, lines);
        }
        Snapshot.allTerms(database, "", lines);
        Snapshot.allTerms(database, "b", lines);
        Snapshot.allTerms(database, "zz", lines);
        for (int doc = 1; doc <= docs; ++doc) {
            Snapshot.document(database, DocId.of(doc), terms, lines);
        }
        for (long slot = 0L; slot < 6L; ++slot) {
            Snapshot.slot(database, new ValueNo(slot), lines);
        }
        try (TermIterator keys = database.metadataKeys("")) {
            while (keys.hasNext()) {
                final String key = keys.next();
                lines.add(String.format("meta %s=%s", key, database.metadata(key)));
            }
        }
        return lines;
    }

    /**
     * First line two snapshots differ in.
     *
     * @param expected Lines expected
     * @param actual Lines read
     * @return Description of the first difference, empty when equal
     */
    static String difference(
        final List<String> expected, final List<String> actual
    ) {
        String result = "";
        final int common = Math.min(expected.size(), actual.size());
        for (int idx = 0; idx < common; ++idx) {
            if (!expected.get(idx).equals(actual.get(idx))) {
                result = String.format(
                    "line %d: expected <%s> but was <%s>", idx,
                    expected.get(idx), actual.get(idx)
                );
                break;
            }
        }
        if (result.isEmpty() && expected.size() != actual.size()) {
            result = String.format(
                "sizes %d and %d, first extra: %s", expected.size(),
                actual.size(),
                Snapshot.longer(expected, actual).get(common)
            );
        }
        return result;
    }

    private static List<String> longer(
        final List<String> left, final List<String> right
    ) {
        List<String> result = left;
        if (right.size() > left.size()) {
            result = right;
        }
        return result;
    }

    private static void term(
        final Database database, final String term, final List<String> lines
    ) {
        lines.add(
            String.format(
                "term %s tf=%s cf=%s exists=%s", term,
                database.termFrequency(term), database.collectionFrequency(term),
                database.termExists(term)
            )
        );
        try (PostingIterator iter = database.postings(term)) {
            while (iter.hasNext()) {
                final DocId doc = iter.next();
                String positions = "";
                if (!term.isEmpty()) {
                    positions = Snapshot.positions(iter.positions());
                }
                lines.add(
                    String.format(
                        "posting %s %s wdf=%s len=%s unique=%s pos=%s", term,
                        doc, iter.wdf(), iter.docLength(), iter.uniqueTerms(),
                        positions
                    )
                );
            }
        }
    }

    private static void allTerms(
        final Database database, final String prefix, final List<String> lines
    ) {
        try (TermIterator iter = database.allTerms(prefix)) {
            while (iter.hasNext()) {
                final String term = iter.next();
                lines.add(
                    String.format(
                        "all[%s] %s tf=%s", prefix, term, iter.termFrequency()
                    )
                );
            }
        }
    }

    private static void document(
        final Database database, final DocId doc, final List<String> terms,
        final List<String> lines
    ) {
        if (Snapshot.present(database, doc)) {
            final Document document = database.document(doc);
            lines.add(
                String.format(
                    "doc %s data=%s values=%s len=%s unique=%s wdfmax=%s",
                    doc, document.data(), Snapshot.values(document.values()),
                    database.docLength(doc), database.uniqueTerms(doc),
                    database.wdfDocMax(doc)
                )
            );
            try (TermIterator iter = database.termList(doc)) {
                while (iter.hasNext()) {
                    final String term = iter.next();
                    lines.add(
                        String.format(
                            "doc %s term %s wdf=%s tf=%s count=%s pos=%s", doc,
                            term, iter.wdf(), iter.termFrequency(),
                            iter.positionCount(),
                            Snapshot.positions(iter.positions())
                        )
                    );
                }
            }
            for (final String term : terms) {
                if (term.isEmpty()) {
                    continue;
                }
                lines.add(
                    String.format(
                        "doc %s positions %s %s %s", doc, term,
                        database.positionCount(doc, term),
                        Snapshot.positions(database.positions(doc, term))
                    )
                );
            }
        } else {
            lines.add(String.format("doc %s absent", doc));
        }
    }

    private static void slot(
        final Database database, final ValueNo slot, final List<String> lines
    ) {
        lines.add(
            String.format(
                "slot %s freq=%s lower=%s upper=%s", slot,
                database.valueFrequency(slot), database.valueLowerBound(slot),
                database.valueUpperBound(slot)
            )
        );
        try (ValueIterator iter = database.valueStream(slot)) {
            while (iter.hasNext()) {
                final String value = iter.next();
                lines.add(
                    String.format("slot %s value %s=%s", slot, iter.docId(), value)
                );
            }
        }
    }

    private static boolean present(final Database database, final DocId doc) {
        boolean result;
        try {
            database.docLength(doc);
            result = true;
        } catch (final DocNotFoundError ex) {
            result = false;
        }
        return result;
    }

    private static String positions(final PositionIterator iter) {
        final List<String> result = new ArrayList<>(0);
        try (iter) {
            while (iter.hasNext()) {
                result.add(iter.next().toString());
            }
        }
        return result.toString();
    }

    private static String values(final ValueIterator iter) {
        final List<String> result = new ArrayList<>(0);
        try (iter) {
            while (iter.hasNext()) {
                final String value = iter.next();
                result.add(String.format("%s=%s", iter.slot(), value));
            }
        }
        return result.toString();
    }
}
