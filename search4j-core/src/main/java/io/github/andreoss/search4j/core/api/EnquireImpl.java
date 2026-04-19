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
import io.github.andreoss.search4j.core.matcher.MatchCursor;
import io.github.andreoss.search4j.core.matcher.Matcher;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of Enquire.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.NullAssignment")
public final class EnquireImpl implements Enquire {

    /**
     * Sort matches by a value slot.
     */
    private static final int SLOT = 2;

    /**
     * Number of best matches an expansion takes as relevant.
     */
    private static final int RELEVANT = 100;

    /**
     * Database the queries run over.
     */
    private final Shard shard;

    /**
     * Query of the next search.
     */
    private Query request;

    /**
     * Weighting scheme of the next search, null for the default.
     */
    private Weight chosen;

    /**
     * Filter applied to each candidate document.
     */
    private MatchDecider filter;

    /**
     * Minimum weight of a match, zero for none.
     */
    private double weightcut;

    /**
     * Percentage cutoff, zero for none.
     */
    private int cutoff;

    /**
     * Time limit of a search, null for none.
     */
    private Duration timelim;

    /**
     * Whether document identifiers ascend among equally ranked matches.
     */
    private boolean forward;

    /**
     * Spy notified of every match.
     */
    private MatchSpy observer;

    /**
     * Key maker used for collapsing.
     */
    private KeyMaker keymake;

    /**
     * Key maker used for key sorting.
     */
    private KeyMaker sorter;

    /**
     * Maximum size of a collapse group, zero for no collapsing.
     */
    private int colmax;

    /**
     * Collapse key selected by the caller.
     */
    private String colkey;

    /**
     * Kind of sorting selected, zero for weight order.
     */
    private int sorttype;

    /**
     * Value slot used for slot sorting.
     */
    private ValueNo sortslot;

    /**
     * Whether the selected sorting is ascending.
     */
    private boolean sortasc;

    /**
     * Creates an enquire over a database.
     *
     * @param shard Database the queries run over
     */
    public EnquireImpl(final Shard shard) {
        this.shard = shard;
        this.cutoff = 0;
        this.weightcut = 0.0;
        this.sortasc = false;
        this.colkey = "";
        this.forward = true;
    }

    @Override
    public void setQuery(final Query query) {
        this.request = query;
    }

    @Override
    public void setQuery(final Query query, final Weight weight) {
        this.request = query;
        this.chosen = weight;
    }

    @Override
    public void setWeightingScheme(final Weight weight) {
        this.chosen = weight;
    }

    @Override
    public MSet getMSet(final int first, final int max) {
        return this.getMSet(first, max, 0);
    }

    @Override
    public MSet getMSet(final int first, final int max, final int atleast) {
        this.filter = null;
        return this.run(first, max, atleast);
    }

    @Override
    public MSet getMSet(
        final int first, final int max, final MatchDecider decider
    ) {
        this.filter = decider;
        return this.run(first, max, 0);
    }

    @Override
    public TermCount getMatchEstimate() {
        final TermCount estimate;
        if (this.request == null) {
            estimate = TermCount.ZERO;
        } else {
            estimate = Matcher.build(
                this.shard, this.request, new BoolWeight()
            ).estimate().resolve(
                this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
            ).max();
        }
        return estimate;
    }

    @Override
    public void setCutoff(final int percent) {
        this.setCutoff(percent, 0.0);
    }

    @Override
    public void setCutoff(final int percent, final double weight) {
        this.cutoff = percent;
        this.weightcut = weight;
    }

    @Override
    public void setTimeLimit(final Duration limit) {
        this.timelim = limit;
    }

    @Override
    public void setDocIdOrder(final boolean order) {
        this.forward = order;
    }

    @Override
    public void setSortByValue(final ValueNo slot, final boolean ascending) {
        this.sorttype = EnquireImpl.SLOT;
        this.sortslot = slot;
        this.sortasc = ascending;
    }

    @Override
    public void setSortByKey(final KeyMaker maker, final boolean ascending) {
        this.sorttype = 3;
        this.sorter = maker;
        this.sortasc = ascending;
    }

    @Override
    public void setMatchSpy(final MatchSpy spy) {
        this.observer = spy;
    }

    @Override
    public void setKeyMaker(final KeyMaker maker) {
        this.keymake = maker;
    }

    @Override
    public void setCollapseMax(final int max) {
        this.colmax = max;
    }

    @Override
    public void setCollapseKey(final String key) {
        this.colkey = key;
    }

    @Override
    public void clear() {
        this.request = null;
        this.chosen = null;
        this.filter = null;
        this.weightcut = 0.0;
        this.cutoff = 0;
        this.timelim = null;
        this.forward = true;
        this.observer = null;
        this.keymake = null;
        this.sorter = null;
        this.colmax = 0;
        this.colkey = "";
        this.sorttype = 0;
        this.sortslot = null;
        this.sortasc = false;
    }

    @Override
    public ESet getESet(final int max) {
        return this.getESet(max, new ProbeWeight());
    }

    @Override
    public ESet getESet(final int max, final ExpandWeight weight) {
        return this.getESet(max, weight, null);
    }

    @Override
    public ESet getESet(
        final int max, final ExpandWeight weight, final ExpandDecider chooser
    ) {
        final ESet result;
        if (this.request == null || this.request.empty()) {
            result = new ESetImpl(List.of());
        } else {
            final List<DocId> relevant = new ArrayList<>(EnquireImpl.RELEVANT);
            for (final MSet.Item item : this.search(0, EnquireImpl.RELEVANT, 0)) {
                relevant.add(item.docId());
            }
            result = new ESetImpl(
                new Expansion(this.shard, this.request, relevant)
                    .items(max, weight, chooser)
            );
        }
        return result;
    }

    private MSet run(final int first, final int max, final int atleast) {
        final MSet result;
        if (this.request == null || this.request.empty()) {
            result = new MSetImpl(
                List.of(), TermCount.ZERO, TermCount.ZERO, TermCount.ZERO,
                TermCount.ZERO, TermCount.ZERO, TermCount.ZERO
            );
        } else {
            result = this.search(first, max, atleast);
        }
        return result;
    }

    private MSet search(final int first, final int max, final int atleast) {
        final long docs = this.shard.docCount().value();
        final int from = (int) Math.min(first, docs);
        final int count = (int) Math.min(max, docs - from);
        final int check = (int) Math.max(Math.min(atleast, docs), from + count);
        Ordering sort = this.ordering();
        sort.refuse(this.cutoff);
        final MatchCursor cursor = Matcher.cursor(
            this.shard, this.request, this.scheme()
        );
        int percent = this.cutoff;
        double factor = percent / 100.0 - Math.ulp(1.0);
        if (cursor.maxWeight() == 0.0) {
            if (sort == Ordering.RELEVANCE) {
                sort = Ordering.DOCID;
            }
            percent = 0;
            factor = 0.0;
        }
        long deadline = 0L;
        if (this.timelim != null) {
            deadline = System.nanoTime() + this.timelim.toNanos();
        }
        int collapse = 0;
        if (this.keymake != null) {
            collapse = this.colmax;
        }
        final Collector collector = new Collector(
            new Limits(
                Math.max(from + count, 1), check, percent, factor, deadline,
                this.sortasc, this.forward, collapse
            ),
            sort, cursor
        );
        final MSet result;
        if (check == 0) {
            result = new MSetImpl(
                List.of(), collector.estimated(this.filter != null)
            );
        } else {
            collector.minimum(this.weightcut);
            this.collect(cursor, collector, sort);
            collector.finish();
            result = new MSetImpl(
                collector.items(from, count == 0, this.shard),
                collector.bounds(this.filter != null)
            );
        }
        return result;
    }

    private Weight scheme() {
        Weight scheme = this.chosen;
        if (scheme == null) {
            scheme = new BM25Weight();
        }
        return scheme;
    }

    private void collect(
        final MatchCursor cursor, final Collector collector, final Ordering sort
    ) {
        boolean more = cursor.next(collector.least());
        while (more) {
            if (this.filter == null || this.vet(cursor, collector)) {
                more = this.consider(cursor, collector, sort);
            }
            more = more && cursor.next(collector.least());
        }
    }

    private boolean vet(final MatchCursor cursor, final Collector collector) {
        final double least = collector.least();
        boolean result = least <= 0.0 || Collector.reaches(cursor.weight(), least);
        if (result) {
            result = this.filter.decide(cursor.docId());
            collector.decide(result);
        }
        return result;
    }

    private boolean consider(
        final MatchCursor cursor, final Collector collector, final Ordering sort
    ) {
        final double least = collector.least();
        final boolean weighed = sort != Ordering.DOCID
            && (sort != Ordering.VALUE || least > 0.0);
        double weight = 0.0;
        if (weighed) {
            weight = cursor.weight();
        }
        final Candidate item = new Candidate(cursor.docId(), weight);
        boolean more = true;
        if ((!weighed || Collector.reaches(weight, least))
            && this.unranked(cursor, collector, sort, item)) {
            if (sort == Ordering.VALUE && !weighed) {
                item.weight(cursor.weight());
            }
            this.report(cursor, item.weight());
            if (collector.collapsing()) {
                item.group(this.group(item.docId()));
            }
            more = collector.process(item);
        }
        return more;
    }

    private boolean unranked(
        final MatchCursor cursor, final Collector collector, final Ordering sort,
        final Candidate item
    ) {
        boolean result = true;
        if (sort == Ordering.VALUE) {
            item.key(this.key(item.docId()));
            result = !collector.worse(item) || !collector.early();
        }
        if (!result) {
            double weight = item.weight();
            if (collector.least() <= 0.0) {
                weight = cursor.weight();
            }
            if (!collector.collapsing()) {
                this.report(cursor, weight);
            }
            collector.reject(weight);
        }
        return result;
    }

    private void report(final MatchCursor cursor, final double weight) {
        if (this.observer != null) {
            this.observer.add(cursor.docId(), weight, cursor.wdf());
        }
    }

    private Ordering ordering() {
        final Ordering result;
        if (this.sorttype == 0) {
            result = Ordering.RELEVANCE;
        } else {
            result = Ordering.VALUE;
        }
        return result;
    }

    private String key(final DocId docid) {
        final String result;
        if (this.sorttype == EnquireImpl.SLOT) {
            result = this.shard.openDocument(docid, false)
                .flatMap(document -> document.value(this.sortslot))
                .orElse("");
        } else {
            result = this.sorter.key(docid);
        }
        return EnquireImpl.text(result);
    }

    private String group(final DocId docid) {
        String result = this.colkey;
        if (result.isEmpty()) {
            result = EnquireImpl.text(this.keymake.key(docid));
        }
        return result;
    }

    private static String text(final String key) {
        String result = key;
        if (result == null) {
            result = "";
        }
        return result;
    }
}
