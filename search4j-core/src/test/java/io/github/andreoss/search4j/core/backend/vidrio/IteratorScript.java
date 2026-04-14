/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes to a database while iterators stay open, recording what the
 * iterators return.
 *
 * @since 0.9.1
 */
final class IteratorScript {

    /**
     * Item count that reads an iterator to its end.
     */
    static final int ALL = Integer.MAX_VALUE;

    /**
     * Database written.
     */
    private final WritableDatabase han;

    /**
     * Items recorded, one line each.
     */
    private final List<String> lines;

    /**
     * Last term taken from a term iterator.
     */
    private String last;

    /**
     * Creates a script over a database.
     *
     * @param han Database written
     */
    IteratorScript(final WritableDatabase han) {
        this.han = han;
        this.lines = new ArrayList<>(0);
        this.last = "";
    }

    /**
     * Database written.
     *
     * @return Database
     */
    WritableDatabase database() {
        return this.han;
    }

    /**
     * Items recorded so far.
     *
     * @return Lines in order
     */
    List<String> lines() {
        return this.lines;
    }

    /**
     * Last term taken by {@link #terms}.
     *
     * @return Term
     */
    String last() {
        return this.last;
    }

    /**
     * Records a line.
     *
     * @param text Line
     */
    void line(final String text) {
        this.lines.add(text);
    }

    /**
     * Adds 1500 documents, commits, then adds 100 more.
     */
    void base() {
        this.bulk(1, 1500);
        this.han.commit();
        this.bulk(1501, 1600);
    }

    /**
     * Adds numbered documents with shared and own terms and values.
     *
     * @param from First number
     * @param upto Last number
     */
    void bulk(final int from, final int upto) {
        for (int num = from; num <= upto; ++num) {
            final Document doc = IteratorScript.doc(
                String.format("doc%d", num), num % 2 == 0, "common",
                String.format("u%d", num % 997), String.format("k%d", num),
                "common"
            );
            if (num % 2 == 1) {
                doc.addTerm("common", TermCount.ONE);
            }
            doc.addValue(new ValueNo(0), String.format("val%07d", num * 31 % 100_003));
            if (num % 5 == 0) {
                doc.addValue(new ValueNo(4), String.format("five%d", num));
            }
            this.han.addDocument(doc);
        }
    }

    /**
     * Opens and closes the postings of a term, which merges its pending
     * postings.
     *
     * @param term Term
     */
    void flush(final String term) {
        this.han.postings(term).close();
    }

    /**
     * Adds documents of two numbered terms each, merging each term.
     *
     * @param first Format of the first term
     * @param second Format of the second term
     * @param upto Number of documents
     */
    void pairs(final String first, final String second, final int upto) {
        for (int num = 0; num < upto; ++num) {
            final String one = String.format(first, num);
            final String two = String.format(second, num);
            this.han.addDocument(IteratorScript.doc("zz", false, one, two));
            this.flush(one);
            this.flush(two);
        }
    }

    /**
     * Records terms with their frequencies.
     *
     * @param terms Iterator
     * @param most Most terms taken
     * @return Number of terms taken
     */
    int terms(final TermIterator terms, final int most) {
        int count = 0;
        while (count < most && terms.hasNext()) {
            this.last = terms.next();
            this.line(String.format("t %s %d", this.last, terms.termFrequency().value()));
            count += 1;
        }
        return count;
    }

    /**
     * Records postings with their frequencies.
     *
     * @param posts Iterator
     * @param most Most postings taken
     */
    void posts(final PostingIterator posts, final int most) {
        for (int count = 0; count < most && posts.hasNext(); ++count) {
            final DocId doc = posts.next();
            this.line(String.format("p %d %d", doc.value(), posts.wdf().value()));
        }
    }

    /**
     * Records values with their documents.
     *
     * @param values Iterator
     * @param most Most values taken
     */
    void values(final ValueIterator values, final int most) {
        for (int count = 0; count < most && values.hasNext(); ++count) {
            final String value = values.next();
            this.line(String.format("v %d %s", values.docId().value(), value));
        }
    }

    /**
     * Creates a document.
     *
     * @param data Data of the document
     * @param placed Whether terms get positions from 1
     * @param terms Terms, each with frequency 1
     * @return Document
     */
    static Document doc(final String data, final boolean placed, final String... terms) {
        final Document doc = DatabaseFactory.document();
        doc.setData(data);
        for (int idx = 0; idx < terms.length; ++idx) {
            if (placed) {
                doc.addPosting(terms[idx], new TermPos(idx + 1), TermCount.ONE);
            } else {
                doc.addTerm(terms[idx], TermCount.ONE);
            }
        }
        return doc;
    }
}
