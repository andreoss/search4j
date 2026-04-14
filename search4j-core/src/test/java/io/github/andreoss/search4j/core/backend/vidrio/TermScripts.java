/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.types.DocId;

/**
 * Scripts keeping term iterators open across writes.
 *
 * @since 0.9.1
 */
final class TermScripts {

    /**
     * Prefix of the numbered terms.
     */
    private static final String KEYS = "k1";

    /**
     * Format of the first terms added.
     */
    private static final String FIRST = "k1zz%d";

    /**
     * Format of the second terms added.
     */
    private static final String SECOND = "k10x%d";

    /**
     * Utility class.
     */
    private TermScripts() {
    }

    /**
     * Terms of a prefix across added terms.
     *
     * @param run Script
     */
    static void added(final IteratorScript run) {
        run.base();
        try (TermIterator terms = run.database().allTerms(TermScripts.KEYS)) {
            run.terms(terms, 50);
            run.pairs(TermScripts.FIRST, TermScripts.SECOND, 300);
            run.terms(terms, IteratorScript.ALL);
        }
    }

    /**
     * Every term with terms added all over the key space.
     *
     * @param run Script
     */
    static void everywhere(final IteratorScript run) {
        run.base();
        final String[] formats = {"a%d", "k1%dx", "m%d", "u5%dq", "zz%d"};
        try (TermIterator terms = run.database().allTerms("")) {
            int round = 0;
            while (run.terms(terms, 97) == 97) {
                for (int idx = 0; idx < 12; ++idx) {
                    final String term = String.format(
                        formats[(round + idx) % formats.length], round * 100 + idx
                    );
                    run.database().addDocument(IteratorScript.doc("r", false, term, "common"));
                    run.flush(term);
                }
                round += 1;
            }
        }
    }

    /**
     * Every term while the tree grows by levels.
     *
     * @param run Script
     */
    static void grown(final IteratorScript run) {
        final WritableDatabase han = run.database();
        for (int num = 1; num <= 5; ++num) {
            han.addDocument(
                IteratorScript.doc("g", false, String.format("g%d", num), "common")
            );
        }
        han.commit();
        try (TermIterator terms = han.allTerms("")) {
            run.terms(terms, 2);
            for (int num = 0; num < 900; ++num) {
                han.addDocument(
                    IteratorScript.doc(
                        "h", num % 2 == 1, String.format("c%04d", num),
                        String.format("gx%04d", num), String.format("zq%04d", num)
                    )
                );
            }
            han.allTerms("").close();
            run.terms(terms, IteratorScript.ALL);
        }
    }

    /**
     * Every term while most terms are removed.
     *
     * @param run Script
     */
    static void shrunk(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        han.commit();
        try (TermIterator terms = han.allTerms("")) {
            run.terms(terms, 10);
            for (int num = 1; num <= 1600; ++num) {
                if (num % 400 != 0) {
                    han.deleteDocument(DocId.of(num));
                }
            }
            han.allTerms("").close();
            run.terms(terms, IteratorScript.ALL);
        }
    }

    /**
     * Terms of a prefix across a commit and the writes after it.
     *
     * @param run Script
     */
    static void committed(final IteratorScript run) {
        run.base();
        final WritableDatabase han = run.database();
        try (TermIterator terms = han.allTerms(TermScripts.KEYS)) {
            run.terms(terms, 50);
            run.pairs(TermScripts.FIRST, TermScripts.SECOND, 100);
            han.commit();
            run.terms(terms, 20);
            for (int num = 1; num <= 1500; num += 4) {
                han.deleteDocument(DocId.of(num));
            }
            run.pairs("k1yy%d", "k13w%d", 100);
            run.flush("common");
            run.terms(terms, IteratorScript.ALL);
        }
    }

    /**
     * Terms of a prefix across a cancelled transaction.
     *
     * @param run Script
     */
    static void cancelled(final IteratorScript run) {
        final WritableDatabase han = run.database();
        run.bulk(1, 1500);
        han.commit();
        han.beginTransaction(true);
        run.bulk(1501, 1600);
        try (TermIterator terms = han.allTerms(TermScripts.KEYS)) {
            run.terms(terms, 50);
            run.pairs(TermScripts.FIRST, TermScripts.SECOND, 150);
            han.cancelTransaction();
            run.terms(terms, 30);
            run.pairs("k1yy%d", "k13w%d", 100);
            run.terms(terms, IteratorScript.ALL);
        }
    }

    /**
     * Metadata keys across added and removed keys.
     *
     * @param run Script
     */
    static void metadata(final IteratorScript run) {
        final WritableDatabase han = run.database();
        for (int num = 0; num < 400; ++num) {
            han.setMetadata(
                String.format("m%04d", num),
                String.format("value-%04d-padding-padding", num)
            );
        }
        han.commit();
        try (TermIterator keys = han.metadataKeys("m")) {
            for (int idx = 0; idx < 50 && keys.hasNext(); ++idx) {
                run.line(String.format("m %s", keys.next()));
            }
            for (int num = 50; num < 350; ++num) {
                han.setMetadata(
                    String.format("m%04dx", num),
                    String.format("extra-%04d-padding-padding", num)
                );
            }
            for (int num = 100; num < 200; ++num) {
                han.setMetadata(String.format("m%04d", num), "");
            }
            while (keys.hasNext()) {
                run.line(String.format("m %s", keys.next()));
            }
        }
    }
}
