/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.api.query.Query;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.matcher.Matcher;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Query expansion: terms of the first matching documents, weighted by an
 * expansion scheme.
 *
 * @since 0.4.0
 */
final class Expansion {

    /**
     * Database the query runs over.
     */
    private final Shard shard;

    /**
     * Query whose matches are expanded.
     */
    private final Query request;

    /**
     * Weighting scheme of the query.
     */
    private final Weight scheme;

    /**
     * Filter of the matching documents.
     */
    private final Predicate<DocId> accepts;

    /**
     * Creates an expansion.
     *
     * @param shard Database the query runs over
     * @param request Query whose matches are expanded
     * @param scheme Weighting scheme of the query
     * @param accepts Filter of the matching documents
     */
    Expansion(
        final Shard shard, final Query request, final Weight scheme,
        final Predicate<DocId> accepts
    ) {
        this.shard = shard;
        this.request = request;
        this.scheme = scheme;
        this.accepts = accepts;
    }

    /**
     * Weighted expansion terms, best first.
     *
     * @param max Maximum number of terms, zero for all
     * @param weight Expansion scheme, null for the default
     * @param chooser Filter of the terms, null for none
     * @return Terms with their weights
     */
    List<ESet.Item> items(
        final int max, final ExpandWeight weight, final ExpandDecider chooser
    ) {
        final Map<String, TermCount> freqs = new HashMap<>(8);
        final Map<String, TermCount> docs = new HashMap<>(8);
        final int matching = this.collect(freqs, docs);
        final ExpandWeight expand;
        if (weight == null) {
            expand = new Bo1EWeight();
        } else {
            expand = weight;
        }
        expand.init(this.shard, this.request, freqs.size());
        final List<ESet.Item> items = new ArrayList<>(freqs.size());
        final ExpandRun run = new ExpandRun(docs, matching, expand);
        run.chooser(chooser);
        run.items(items);
        for (final Map.Entry<String, TermCount> entry : freqs.entrySet()) {
            run.add(entry.getKey(), entry.getValue());
        }
        items.sort(
            Comparator.comparingDouble(ESet.Item::weight).reversed()
        );
        if (max > 0 && items.size() > max) {
            items.subList(max, items.size()).clear();
        }
        return items;
    }

    private int collect(
        final Map<String, TermCount> freqs, final Map<String, TermCount> docs
    ) {
        final PostList list = Matcher.resolve(this.shard, this.request, this.scheme);
        DocId docid = list.next().docId();
        int matching = 0;
        while (!docid.equals(DocId.NONE) && matching < 100) {
            if (this.accepts.test(docid)) {
                matching = matching + 1;
                this.terms(docid, freqs, docs);
            }
            docid = list.next().docId();
        }
        return matching;
    }

    private void terms(
        final DocId docid, final Map<String, TermCount> freqs,
        final Map<String, TermCount> docs
    ) {
        final Document document = this.shard
            .openDocument(docid, false).orElse(null);
        if (document != null) {
            final TermIterator terms = document.termList();
            while (terms.hasNext()) {
                final String term = terms.next();
                if (!term.isEmpty()) {
                    freqs.merge(term, terms.wdf(), TermCount::plus);
                    docs.merge(term, new TermCount(1), TermCount::plus);
                }
            }
        }
    }
}
