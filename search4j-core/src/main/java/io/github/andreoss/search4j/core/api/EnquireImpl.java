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
import io.github.andreoss.search4j.core.matcher.MatchEstimate;
import io.github.andreoss.search4j.core.matcher.MatchEstimates;
import io.github.andreoss.search4j.core.matcher.Matcher;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Default implementation of Enquire.
 *
 * @since 0.1.0
 */
public final class EnquireImpl implements Enquire {

    private final Shard shard;
    private Query query;
    private Weight weight;
    private int first;
    private int maxItems;
    private MatchDecider matchDecider;
    private int checkAtLeast;
    private int percentCutoff;
    private Duration timeLimit;
    private boolean docIdOrder;
    private MatchSpy matchSpy;
    private KeyMaker keyMaker;
    private int collapseMax;
    private String collapseKey;
    private SortSpec sortSpec;

    public EnquireImpl(final Shard shard) {
        this.shard = shard;
        this.first = 0;
        this.maxItems = 0;
        this.percentCutoff = 0;
        this.timeLimit = null;
        this.docIdOrder = false;
    }

    @Override
    public void setQuery(final Query query) {
        this.query = query;
        this.weight = null;
    }

    @Override
    public void setQuery(final Query query, final Weight weight) {
        this.query = query;
        this.weight = weight;
    }

    @Override
    public void setWeightingScheme(final Weight weight) {
        this.weight = weight;
    }

    @Override
    public MSet getMSet(final int first, final int maxItems) {
        this.first = first;
        this.maxItems = maxItems;
        return this.execute(null);
    }

    @Override
    public MSet getMSet(
        final int first, final int maxItems, final MatchDecider matchDecider
    ) {
        this.first = first;
        this.maxItems = maxItems;
        this.matchDecider = matchDecider;
        return this.execute(matchDecider);
    }

    @Override
    public MSet getMSet(
        final int first, final int maxItems, final MatchDecider matchDecider,
        final int checkAtLeast
    ) {
        this.first = first;
        this.maxItems = maxItems;
        this.matchDecider = matchDecider;
        this.checkAtLeast = checkAtLeast;
        return this.execute(matchDecider);
    }

    @Override
    public TermCount getMatchEstimate() {
        if (this.query == null) {
            return TermCount.ZERO;
        }
        final PostList postList = Matcher.resolve(this.shard, this.query);
        final MatchEstimates est = MatchEstimate.from(postList).resolve(
            this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
        );
        return est.max();
    }

    @Override
    public void setCutoff(final int percentCutoff) {
        this.percentCutoff = percentCutoff;
    }

    @Override
    public void setTimeLimit(final Duration timeLimit) {
        this.timeLimit = timeLimit;
    }

    @Override
    public void setDocIdOrder(final boolean order) {
        this.docIdOrder = order;
        this.sortSpec = null;
    }

    @Override
    public void setSortByValue(final ValueNo slot, final boolean ascending) {
        this.sortSpec = new SortSpec(SortSpec.Type.VALUE, slot, null, ascending);
        this.docIdOrder = false;
    }

    @Override
    public void setSortByKey(final KeyMaker keyMaker, final boolean ascending) {
        this.sortSpec = new SortSpec(SortSpec.Type.KEY, null, keyMaker, ascending);
        this.docIdOrder = false;
    }

    @Override
    public void setMatchSpy(final MatchSpy matchSpy) {
        this.matchSpy = matchSpy;
    }

    @Override
    public void setKeyMaker(final KeyMaker keyMaker) {
        this.keyMaker = keyMaker;
    }

    @Override
    public void setCollapseMax(final int maxMax) {
        this.collapseMax = maxMax;
    }

    @Override
    public void setCollapseKey(final String collapseKey) {
        this.collapseKey = collapseKey;
    }

    @Override
    public void clear() {
        this.query = null;
        this.weight = null;
        this.first = 0;
        this.maxItems = 0;
        this.matchDecider = null;
        this.checkAtLeast = 0;
        this.percentCutoff = 0;
        this.timeLimit = null;
        this.docIdOrder = false;
        this.matchSpy = null;
        this.keyMaker = null;
        this.collapseMax = 0;
        this.collapseKey = "";
        this.sortSpec = null;
    }

    private MSet execute(final MatchDecider decider) {
        if (this.query == null || this.query.empty()) {
            return new MSetImpl(List.of(), new TermCount(0), new TermCount(0));
        }

        final Weight scheme = this.weight != null ? this.weight : new BoolWeight();
        final io.github.andreoss.search4j.core.backend.ShardStatistics stats = this.shard;
        scheme.init(stats, this.query, this.query.length().value());

        final PostList postList = Matcher.resolve(this.shard, this.query, scheme);

        final List<MSet.Item> items = new ArrayList<>();
        DocId docId = postList.next().docId();
        int rank = 1;
        int checked = 0;
        int added = 0;

        // Collapsing support
        String prevCollapseKey = null;
        int collapseCount = 0;

        while (!docId.equals(DocId.NONE)) {
            checked++;
            boolean include = true;

            if (decider != null) {
                include = decider.decide(docId);
            }

            // MatchSpy notification
            if (this.matchSpy != null) {
                final double w = postList.weight(
                    new io.github.andreoss.search4j.core.backend.list.DocStats(
                        this.shard.docLength(docId),
                        this.shard.uniqueTerms(docId),
                        this.shard.wdfDocMax(docId)
                    )
                );
                this.matchSpy.add(docId, w, postList.wdf());
            }

            // Collapsing logic
            if (include && this.collapseMax > 0 && this.keyMaker != null) {
                final String key = this.keyMaker.key(docId);
                if (key != null && !key.isEmpty()) {
                    if (prevCollapseKey != null && key.equals(prevCollapseKey)) {
                        collapseCount++;
                        if (collapseCount >= this.collapseMax) {
                            include = false;
                        }
                    } else {
                        prevCollapseKey = key;
                        collapseCount = 1;
                    }
                }
            }

            if (include) {
                added++;
                if (added > this.first) {
                    final double w = postList.weight(
                        new io.github.andreoss.search4j.core.backend.list.DocStats(
                            this.shard.docLength(docId),
                            this.shard.uniqueTerms(docId),
                            this.shard.wdfDocMax(docId)
                        )
                    );
                    items.add(new MSetItemImpl(docId, w, rank, this.shard));
                    rank++;
                    if (this.maxItems > 0 && items.size() >= this.maxItems) {
                        break;
                    }
                }
            }

            if (this.checkAtLeast > 0 && checked >= this.checkAtLeast
                && items.size() >= this.maxItems && this.maxItems > 0) {
                break;
            }

            // Percentage cutoff
            if (this.percentCutoff > 0 && added > 0) {
                final int percent = (added * 100) / checked;
                if (percent >= this.percentCutoff) {
                    break;
                }
            }

            docId = postList.next().docId();
        }

        // Apply sorting if specified
        if (this.sortSpec != null) {
            this.applySort(items);
        } else if (this.docIdOrder) {
            items.sort(Comparator.comparing(MSet.Item::docId));
        }

        // Re-rank after sorting
        for (int i = 0; i < items.size(); i++) {
            ((MSetItemImpl) items.get(i)).setRank(i + 1);
        }

        final MatchEstimates est = MatchEstimate.from(postList).resolve(
            this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
        );
        return new MSetImpl(items, est.max(), est.max());
    }

    private void applySort(final List<MSet.Item> items) {
        final Comparator<MSet.Item> comparator = switch (this.sortSpec.type) {
            case VALUE -> {
                final ValueNo slot = this.sortSpec.valueSlot;
                yield (a, b) -> {
                    final String av = this.shard.openDocument(a.docId(), false)
                        .map(d -> d.value(slot).orElse("")).orElse("");
                    final String bv = this.shard.openDocument(b.docId(), false)
                        .map(d -> d.value(slot).orElse("")).orElse("");
                    return this.sortSpec.ascending ? av.compareTo(bv) : bv.compareTo(av);
                };
            }
            case KEY -> {
                final KeyMaker keyMaker = this.sortSpec.keyMaker;
                yield (a, b) -> {
                    final String ak = keyMaker.key(a.docId());
                    final String bk = keyMaker.key(b.docId());
                    final int cmp = (ak == null ? "" : ak).compareTo(bk == null ? "" : bk);
                    return this.sortSpec.ascending ? cmp : -cmp;
                };
            }
        };
        items.sort(comparator);
    }

    private static final class MSetItemImpl implements MSet.Item {

        private final DocId docId;
        private final double weight;
        private int rank;
        private final Shard shard;

        MSetItemImpl(
            final DocId docId, final double weight, final int rank,
            final Shard shard
        ) {
            this.docId = docId;
            this.weight = weight;
            this.rank = rank;
            this.shard = shard;
        }

        @Override
        public DocId docId() {
            return this.docId;
        }

        @Override
        public double weight() {
            return this.weight;
        }

        @Override
        public int rank() {
            return this.rank;
        }

        /**
         * Updates the rank after sorting.
         *
         * @param rank New rank (one-based)
         */
        void setRank(final int rank) {
            this.rank = rank;
        }

        @Override
        public Document document() {
            return this.shard.openDocument(this.docId, false)
                .orElseThrow(() -> new IllegalStateException(
                    "Document " + this.docId + " not found"
                ));
        }
    }

    private static final class MSetImpl implements MSet {

        private final List<Item> items;
        private final TermCount size;
        private final TermCount maxSize;

        MSetImpl(
            final List<Item> items, final TermCount size, final TermCount maxSize
        ) {
            this.items = items;
            this.size = size;
            this.maxSize = maxSize;
        }

        @Override
        public TermCount size() {
            return this.size;
        }

        @Override
        public TermCount maxSize() {
            return this.maxSize;
        }

        @Override
        public Item first() {
            if (this.items.isEmpty()) {
                throw new NoSuchElementException("Empty MSet");
            }
            return this.items.get(0);
        }

        @Override
        public Item get(final int index) {
            if (index < 0 || index >= this.items.size()) {
                throw new IndexOutOfBoundsException(
                    String.format("Index %d, size %d", index, this.items.size())
                );
            }
            return this.items.get(index);
        }

        @Override
        public List<Item> toList() {
            return List.copyOf(this.items);
        }

        @Override
        public java.util.Iterator<Item> iterator() {
            return this.items.iterator();
        }
    }

    @Override
    public ESet getESet(final int maxTerms) {
        return this.getESet(maxTerms, new Bo1EWeight());
    }

    @Override
    public ESet getESet(final int maxTerms, final ExpandWeight weight) {
        return this.getESet(maxTerms, weight, null);
    }

    @Override
    public ESet getESet(
        final int maxTerms,
        final ExpandWeight weight,
        final ExpandDecider decider
    ) {
        if (this.query == null || this.query.empty()) {
            return new ESetImpl(List.of());
        }

        final Weight scheme = this.weight != null ? this.weight : new BoolWeight();
        final io.github.andreoss.search4j.core.backend.ShardStatistics stats = this.shard;
        scheme.init(stats, this.query, this.query.length().value());

        final PostList postList = Matcher.resolve(this.shard, this.query, scheme);

        // Collect term frequencies from top matching documents
        final java.util.Map<String, TermCount> termFreqs = new java.util.HashMap<>();
        final java.util.Map<String, TermCount> docFreqs = new java.util.HashMap<>();
        int matchingDocs = 0;
        DocId docId = postList.next().docId();
        int checked = 0;

        while (!docId.equals(DocId.NONE) && checked < 100) {
            checked++;
            boolean include = true;

            if (this.matchDecider != null) {
                include = this.matchDecider.decide(docId);
            }

            if (include) {
                matchingDocs++;
                final io.github.andreoss.search4j.core.api.Document doc =
                    this.shard.openDocument(docId, false).orElse(null);
                if (doc != null) {
                    final io.github.andreoss.search4j.core.api.TermIterator ti = doc.termList();
                    while (ti.hasNext()) {
                        final String term = ti.next();
                        if (!term.isEmpty()) {
                            termFreqs.merge(term, ti.wdf(), TermCount::plus);
                            docFreqs.merge(term, new TermCount(1), TermCount::plus);
                        }
                    }
                }
            }
            docId = postList.next().docId();
        }

        // Compute expansion weights
        final ExpandWeight ew = weight != null ? weight : new Bo1EWeight();
        ew.init(stats, this.query, termFreqs.size());

        final List<ESet.Item> items = new ArrayList<>();
        for (final java.util.Map.Entry<String, TermCount> entry : termFreqs.entrySet()) {
            final String term = entry.getKey();
            final TermCount termFreq = entry.getValue();
            final TermCount docFreq = docFreqs.getOrDefault(term, new TermCount(0));
            final TermCount matching = new TermCount(matchingDocs);

            if (decider != null && !decider.decide(term, termFreq)) {
                continue;
            }

            final double w = ew.weight(term, TermCount.ONE, termFreq, docFreq, matching);
            if (w > 0.0) {
                items.add(new ESetItemImpl(term, termFreq, w));
            }
        }

        // Sort by weight descending
        items.sort((a, b) -> Double.compare(b.weight(), a.weight()));

        // Limit to maxTerms
        if (maxTerms > 0 && items.size() > maxTerms) {
            items.subList(maxTerms, items.size()).clear();
        }

        return new ESetImpl(items);
    }

    private static final class ESetItemImpl implements ESet.Item {

        private final String term;
        private final TermCount estimatedTermFrequency;
        private final double weight;

        ESetItemImpl(
            final String term,
            final TermCount estimatedTermFrequency,
            final double weight
        ) {
            this.term = term;
            this.estimatedTermFrequency = estimatedTermFrequency;
            this.weight = weight;
        }

        @Override
        public String term() {
            return this.term;
        }

        @Override
        public TermCount estimatedTermFrequency() {
            return this.estimatedTermFrequency;
        }

        @Override
        public double weight() {
            return this.weight;
        }
    }

    private static final class ESetImpl implements ESet {

        private final List<Item> items;

        ESetImpl(final List<Item> items) {
            this.items = items;
        }

        @Override
        public TermCount size() {
            return new TermCount(this.items.size());
        }

        @Override
        public Item first() {
            if (this.items.isEmpty()) {
                throw new NoSuchElementException("Empty ESet");
            }
            return this.items.get(0);
        }

        @Override
        public Item get(final int index) {
            if (index < 0 || index >= this.items.size()) {
                throw new IndexOutOfBoundsException(
                    String.format("Index %d, size %d", index, this.items.size())
                );
            }
            return this.items.get(index);
        }

        @Override
        public List<Item> toList() {
            return List.copyOf(this.items);
        }

        @Override
        public java.util.Iterator<Item> iterator() {
            return this.items.iterator();
        }
    }

    /**
     * Sorting specification for Enquire.
     */
    private static final class SortSpec {

        enum Type { VALUE, KEY }

        private final Type type;
        private final ValueNo valueSlot;
        private final KeyMaker keyMaker;
        private final boolean ascending;

        SortSpec(
            final Type type, final ValueNo valueSlot,
            final KeyMaker keyMaker, final boolean ascending
        ) {
            this.type = type;
            this.valueSlot = valueSlot;
            this.keyMaker = keyMaker;
            this.ascending = ascending;
        }
    }
}
