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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

        while (!docId.equals(DocId.NONE)) {
            checked++;
            boolean include = true;

            if (decider != null) {
                include = decider.decide(docId);
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

            docId = postList.next().docId();
        }

        final MatchEstimates est = MatchEstimate.from(postList).resolve(
            this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
        );
        return new MSetImpl(items, est.max(), est.max());
    }

    private static final class MSetItemImpl implements MSet.Item {

        private final DocId docId;
        private final double weight;
        private final int rank;
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
}
