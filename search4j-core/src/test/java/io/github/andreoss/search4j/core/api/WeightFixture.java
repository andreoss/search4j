/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.QueryOp;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Shared corpus and queries of the captured weight vectors, built in the
 * in-memory backend.
 *
 * @since 0.1.0
 */
final class WeightFixture {

    /**
     * Term delimiter of the fixtures.
     */
    private static final Pattern SPACE = Pattern.compile(" ");

    /**
     * Utility class, no instances.
     */
    private WeightFixture() {
    }

    /**
     * Runs a captured query with a scheme.
     *
     * @param name Query identifier
     * @param weight Weighting scheme
     * @return Every match
     */
    static MSet search(final String name, final Weight weight) {
        final String[] query = WeightFixture.query(name);
        final Enquire enquire = new EnquireImpl(WeightFixture.shard(query[1]));
        enquire.setQuery(WeightFixture.build(query[4], query[5]), weight);
        return enquire.getMSet(0, 1000);
    }

    static InMemoryShard valued(final String corpus) {
        final Map<Integer, DocumentInternal> docs = new TreeMap<>();
        try {
            for (final String[] row : WeightVectors.rows("weight-corpus.tsv")) {
                if (row[0].equals(corpus)) {
                    docs.put(
                        Integer.parseInt(row[1]), WeightFixture.document(row[2])
                    );
                }
            }
            for (final String[] row : WeightVectors.rows("matcher-values.tsv")) {
                if (row[0].equals(corpus)) {
                    docs.get(Integer.parseInt(row[1])).addValue(
                        new ValueNo(Long.parseLong(row[2])), row[3]
                    );
                }
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        final InMemoryShard shard = new InMemoryShard();
        for (final Map.Entry<Integer, DocumentInternal> doc : docs.entrySet()) {
            shard.replaceDocument(DocId.of(doc.getKey()), doc.getValue());
        }
        return shard;
    }

    private static String[] query(final String name) {
        try {
            String[] found = new String[0];
            for (final String[] row : WeightVectors.rows("weight-queries.tsv")) {
                if (row[0].equals(name)) {
                    found = row;
                }
            }
            return found;
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static InMemoryShard shard(final String corpus) {
        final InMemoryShard shard = new InMemoryShard();
        try {
            for (final String[] row : WeightVectors.rows("weight-corpus.tsv")) {
                if (row[0].equals(corpus)) {
                    shard.replaceDocument(
                        DocId.of(Integer.parseInt(row[1])),
                        WeightFixture.document(row[2])
                    );
                }
            }
        } catch (final IOException ex) {
            throw new IllegalStateException(ex);
        }
        return shard;
    }

    private static DocumentInternal document(final String terms) {
        final DocumentInternal doc = new DocumentInternal();
        for (final String term : WeightFixture.SPACE.split(terms, -1)) {
            final int colon = term.indexOf(':');
            doc.addTerm(
                term.substring(0, colon),
                WeightFixture.count(term.substring(colon + 1))
            );
        }
        return doc;
    }

    private static Query build(final String operator, final String terms) {
        final List<Query> leaves = new ArrayList<>(8);
        for (final String term : WeightFixture.SPACE.split(terms, -1)) {
            final int colon = term.indexOf(':');
            if (colon < 0) {
                leaves.add(Query.term(term));
            } else {
                leaves.add(
                    Query.term(
                        term.substring(0, colon),
                        WeightFixture.count(term.substring(colon + 1))
                    )
                );
            }
        }
        final Query result;
        if ("TERM".equals(operator)) {
            result = leaves.get(0);
        } else {
            result = Query.of(QueryOp.valueOf(operator), leaves);
        }
        return result;
    }

    private static TermCount count(final String text) {
        return new TermCount(Long.parseLong(text));
    }
}
