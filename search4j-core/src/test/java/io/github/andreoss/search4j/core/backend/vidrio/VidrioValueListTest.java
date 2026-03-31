/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioValueList} and {@link ValueTable} over random
 * values written across revisions, read back per slot and per document.
 *
 * @since 0.1.0
 */
final class VidrioValueListTest {

    /**
     * Slot zero.
     */
    private static final ValueNo ZERO = new ValueNo(0L);

    @Test
    void readsRandomValuesAcrossRevisions() {
        final ValueModel model = new ValueModel(18L);
        final Values values = Values.of();
        final List<String> failures = new ArrayList<>(0);
        for (int revision = 1; revision <= 4; ++revision) {
            for (int step = 0; step < 400; ++step) {
                model.change(values.writer());
            }
            failures.addAll(model.check(values.writer()));
            failures.addAll(model.check(values.commit()));
        }
        Assertions.assertEquals(List.of(), failures, "Every read matches the model");
    }

    @Test
    void skipsAndChecksAcrossChunks() {
        final List<Object> seen = new ArrayList<>(16);
        final Values values = Values.of();
        final NavigableMap<DocId, String> docs = new TreeMap<>();
        for (int idx = 1; idx <= 60; ++idx) {
            docs.put(DocId.of(3 * idx), String.format("%03d", idx).repeat(40));
        }
        for (final Map.Entry<DocId, String> doc : docs.entrySet()) {
            values.writer().add(doc.getKey(), Values.map(0, doc.getValue()));
        }
        final ValueTable table = values.commit();
        final List<DocId> chunks = values.chunks(0);
        seen.add(chunks.size() >= 3);
        final VidrioValueList list = table.open(VidrioValueListTest.ZERO);
        list.skipTo(chunks.get(1));
        seen.add(list.docId());
        list.skipTo(DocId.of(chunks.get(1).value() - 1));
        seen.add(list.docId());
        list.skipTo(DocId.of(chunks.get(2).value() - 1));
        seen.add(list.docId());
        seen.add(list.value().equals(docs.get(chunks.get(2))));
        list.next();
        seen.add(list.docId());
        list.skipTo(DocId.of(181));
        seen.add(list.atEnd());
        list.next();
        seen.add(list.atEnd());
        final VidrioValueList checked = table.open(VidrioValueListTest.ZERO);
        seen.add(checked.check(DocId.of(4)));
        seen.add(checked.docId());
        seen.add(checked.check(chunks.get(1)));
        seen.add(checked.docId());
        seen.add(checked.check(DocId.of(chunks.get(2).value() - 1)));
        checked.next();
        seen.add(checked.docId());
        seen.add(checked.check(DocId.of(179)));
        seen.add(checked.check(DocId.of(181)));
        checked.next();
        seen.add(checked.atEnd());
        Assertions.assertEquals(
            List.of(
                true, chunks.get(1), chunks.get(1), chunks.get(2), true,
                DocId.of(chunks.get(2).value() + 3), true, true,
                true, DocId.of(6), true, chunks.get(1), false, chunks.get(2),
                true, false, true
            ),
            seen,
            "Skips to and between chunk starts, checks in and between chunks"
        );
    }

    @Test
    void refusesReadsOffDocument() {
        final List<String> seen = new ArrayList<>(12);
        final Values values = Values.of();
        values.writer().add(DocId.of(2), Values.map(0, "b"));
        values.writer().add(DocId.of(3), Values.map(1, "c"));
        final ValueTable table = values.commit();
        final VidrioValueList list = table.open(VidrioValueListTest.ZERO);
        seen.add(VidrioValueListTest.refused(list::docId));
        list.next();
        seen.add(VidrioValueListTest.refused(list::value));
        seen.add(String.valueOf(list.check(DocId.of(9))));
        seen.add(VidrioValueListTest.refused(list::docId));
        list.next();
        seen.add(VidrioValueListTest.refused(list::docId));
        seen.add(String.valueOf(list.check(DocId.of(2))));
        list.skipTo(DocId.of(1));
        seen.add(String.valueOf(list.atEnd()));
        final VidrioValueList empty = table.open(new ValueNo(5L));
        seen.add(String.valueOf(empty.check(DocId.of(1))));
        empty.next();
        seen.add(String.valueOf(empty.atEnd()));
        final VidrioValueList skipped = table.open(new ValueNo(1L));
        skipped.skipTo(DocId.of(9));
        seen.add(String.valueOf(skipped.atEnd()));
        seen.add(skipped.description());
        seen.add(String.valueOf(skipped.slot()));
        Assertions.assertEquals(
            List.of(
                "Value list is not on a document", "none",
                "false", "Value list is not on a document",
                "Value list is not on a document", "false", "true", "false",
                "true", "true", "VidrioValueList(slot=1)", "1"
            ),
            seen,
            "Before the first move, after a failed check, after the end"
        );
    }

    private static String refused(final Runnable call) {
        String result = "none";
        try {
            call.run();
        } catch (final InvalidOperationError ex) {
            result = ex.getMessage();
        }
        return result;
    }
}
