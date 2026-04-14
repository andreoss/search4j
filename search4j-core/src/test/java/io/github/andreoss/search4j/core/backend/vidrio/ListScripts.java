/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Scripts keeping posting and value iterators open across
 * writes.
 *
 * @since 0.9.1
 */
final class ListScripts {

    /**
     * Term of every numbered document.
     */
    private static final String COMMON = "common";

    /**
     * Utility class.
     */
    private ListScripts() {
    }

    /**
     * Postings of a term across removed postings.
     *
     * @param run Script
     */
    static void removed(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (PostingIterator posts = han.postings(ListScripts.COMMON)) {
            run.posts(posts, 200);
            for (int num = 300; num <= 1500; num += 2) {
                han.deleteDocument(DocId.of(num));
            }
            run.flush(ListScripts.COMMON);
            run.posts(posts, IteratorScript.ALL);
        }
    }

    /**
     * Every document across added and removed documents.
     *
     * @param run Script
     */
    static void documents(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (PostingIterator posts = han.postings("")) {
            run.posts(posts, 300);
            run.bulk(1601, 1800);
            for (int num = 400; num <= 700; ++num) {
                han.deleteDocument(DocId.of(num));
            }
            run.flush("");
            run.posts(posts, IteratorScript.ALL);
        }
    }

    /**
     * Every document of a database without gaps, skipped across added and
     * removed documents.
     *
     * @param run Script
     */
    static void leapedAll(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (PostingIterator posts = han.postings("")) {
            run.posts(posts, 3);
            posts.skipTo(DocId.of(4));
            run.posts(posts, 5);
            run.bulk(1601, 1700);
            for (int num = 1500; num <= 1560; ++num) {
                han.deleteDocument(DocId.of(num));
            }
            run.flush("");
            posts.skipTo(DocId.of(1550));
            run.posts(posts, IteratorScript.ALL);
        }
    }

    /**
     * Every document opened while no document is left, then documents added.
     *
     * @param run Script
     */
    static void emptied(final IteratorScript run) {
        final WritableDatabase han = run.database();
        for (int idx = 0; idx < 300; ++idx) {
            final Document doc = IteratorScript.doc("", false, "x");
            doc.addTerm(String.format("y%d", idx), new TermCount(idx % 7 + 1));
            han.addDocument(doc);
        }
        han.commit();
        for (int num = 1; num <= 300; ++num) {
            han.deleteDocument(DocId.of(num));
        }
        run.flush("");
        for (int idx = 0; idx < 5; ++idx) {
            han.addDocument(IteratorScript.doc("", false, "z"));
        }
    }

    /**
     * Values of a slot across value changes merged by another stream.
     *
     * @param run Script
     */
    static void merged(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (ValueIterator values = han.valueStream(new ValueNo(0))) {
            run.values(values, 300);
            for (int idx = 0; idx < 400; ++idx) {
                final Document doc = IteratorScript.doc("vv", false, "vv");
                doc.addValue(new ValueNo(0), String.format("zzz%05d", idx));
                doc.addValue(new ValueNo(4), "f");
                han.addDocument(doc);
            }
            for (int num = 1; num <= 800; num += 9) {
                han.deleteDocument(DocId.of(num));
            }
            han.valueStream(new ValueNo(4)).close();
            run.values(values, IteratorScript.ALL);
        }
    }

    /**
     * Values of a slot skipped across value changes.
     *
     * @param run Script
     */
    static void leaped(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (ValueIterator values = han.valueStream(new ValueNo(4))) {
            run.values(values, 30);
            run.bulk(1601, 2400);
            han.valueStream(new ValueNo(0)).close();
            values.skipTo(DocId.of(1000));
            run.values(values, 5);
            for (int num = 1000; num <= 2000; num += 2) {
                han.deleteDocument(DocId.of(num));
            }
            han.valueStream(new ValueNo(0)).close();
            values.skipTo(DocId.of(1990));
            run.values(values, IteratorScript.ALL);
        }
    }
}
