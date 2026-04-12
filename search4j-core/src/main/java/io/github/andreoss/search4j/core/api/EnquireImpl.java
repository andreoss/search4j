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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of Enquire.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.CouplingBetweenObjects",
    "PMD.TooManyFields",
    "PMD.NullAssignment"
})
public final class EnquireImpl implements Enquire {

    /**
     * Sort matches by a value slot.
     */
    private static final int SLOT = 2;

    /**
     * Database the queries run over.
     */
    private final Shard shard;

    /**
     * Query of the next search.
     */
    private Query request;

    /**
     * Weighting scheme of the next search.
     */
    private Weight chosen;

    /**
     * Zero-based index of the first match to return.
     */
    private int offset;

    /**
     * Maximum number of matches to return, zero for all.
     */
    private int maxitem;

    /**
     * Filter applied to each candidate document.
     */
    private MatchDecider filter;

    /**
     * Minimum number of documents checked before an early stop.
     */
    private int atleast;

    /**
     * Percentage cutoff, zero for none.
     */
    private int cutoff;

    /**
     * Time limit of a search, null for none.
     */
    private Duration timelim;

    /**
     * Whether matches are ordered by document identifier.
     */
    private boolean idorder;

    /**
     * Spy notified of every match.
     */
    private MatchSpy observer;

    /**
     * Key maker used for collapsing and key sorting.
     */
    private KeyMaker keymake;

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
        this.offset = 0;
        this.maxitem = 0;
        this.cutoff = 0;
        this.sortasc = false;
        this.colkey = "";
    }

    @Override
    public void setQuery(final Query query) {
        this.request = query;
        this.chosen = null;
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
        return this.getMSet(first, max, null);
    }

    @Override
    public MSet getMSet(
        final int first, final int max, final MatchDecider decider
    ) {
        this.offset = first;
        this.maxitem = max;
        this.filter = decider;
        final MSet result;
        if (this.request == null || this.request.empty()) {
            result = new MSetImpl(
                List.of(), TermCount.ZERO, TermCount.ZERO
            );
        } else {
            result = this.search();
        }
        return result;
    }

    @Override
    public TermCount getMatchEstimate() {
        final TermCount estimate;
        if (this.request == null) {
            estimate = TermCount.ZERO;
        } else {
            estimate = MatchEstimate.from(
                Matcher.resolve(this.shard, this.request)
            ).resolve(
                this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
            ).max();
        }
        return estimate;
    }

    @Override
    public void setCutoff(final int percent) {
        this.cutoff = percent;
    }

    @Override
    public void setTimeLimit(final Duration limit) {
        this.timelim = limit;
    }

    @Override
    public void setDocIdOrder(final boolean order) {
        this.idorder = order;
        this.sorttype = 0;
    }

    @Override
    public void setSortByValue(final ValueNo slot, final boolean ascending) {
        this.sorttype = EnquireImpl.SLOT;
        this.sortslot = slot;
        this.sortasc = ascending;
        this.idorder = false;
    }

    @Override
    public void setSortByKey(final KeyMaker maker, final boolean ascending) {
        this.sorttype = 3;
        this.keymake = maker;
        this.sortasc = ascending;
        this.idorder = false;
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
        this.offset = 0;
        this.maxitem = 0;
        this.filter = null;
        this.atleast = 0;
        this.cutoff = 0;
        this.timelim = null;
        this.idorder = false;
        this.observer = null;
        this.keymake = null;
        this.colmax = 0;
        this.colkey = "";
        this.sorttype = 0;
        this.sortslot = null;
        this.sortasc = false;
    }

    @Override
    public ESet getESet(final int max) {
        return this.getESet(max, new Bo1EWeight());
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
            result = new ESetImpl(this.expand(max, weight, chooser));
        }
        return result;
    }

    private MSet search() {
        final Weight scheme;
        if (this.chosen == null) {
            scheme = new BoolWeight();
        } else {
            scheme = this.chosen;
        }
        final PostList list = Matcher.resolve(this.shard, this.request, scheme);
        final MatchState state = new MatchState(this.shard, list);
        final long start = System.nanoTime();
        state.advance();
        while (!state.list().atEnd() && !this.stop(state, start)) {
            this.consider(state.list().docId(), state);
            state.advance();
        }
        this.order(state.found());
        state.rank();
        final MatchEstimates max = MatchEstimate.from(list).resolve(
            this.shard.docCount(), DocId.of(1), this.shard.lastDocId()
        );
        return new MSetImpl(state.found(), max.max(), max.max());
    }

    private boolean stop(final MatchState state, final long start) {
        final boolean stop;
        if (this.timelim != null
            && System.nanoTime() - start > this.timelim.toNanos()) {
            stop = true;
        } else {
            stop = this.limit(state);
        }
        return stop;
    }

    private boolean limit(final MatchState state) {
        boolean stop = false;
        if (this.maxitem > 0 && state.found().size() >= this.maxitem) {
            stop = true;
        } else if (this.atleast > 0 && state.seen() >= this.atleast
            && this.maxitem > 0 && state.found().size() >= this.maxitem) {
            stop = true;
        } else if (this.cutoff > 0 && state.added() > 0) {
            stop = this.cut(state);
        }
        return stop;
    }

    private boolean cut(final MatchState state) {
        final double top = EnquireImpl.top(state);
        return top > 0.0 && state.found()
            .get(state.found().size() - 1).weight() * 100.0
            < top * this.cutoff;
    }

    private static double top(final MatchState state) {
        double top = 0.0;
        for (final MSet.Item item : state.found()) {
            if (item.weight() > top) {
                top = item.weight();
            }
        }
        return top;
    }

    private void consider(final DocId docid, final MatchState state) {
        state.check();
        if (this.accepts(docid, state)) {
            final double score = state.score(docid);
            this.report(docid, state, score);
            state.drop();
            if (state.added() > this.offset) {
                state.add(docid, score);
            }
        } else {
            this.report(docid, state, state.score(docid));
        }
    }

    private void report(
        final DocId docid, final MatchState state, final double score
    ) {
        if (this.observer != null) {
            this.observer.add(docid, score, state.list().wdf());
        }
    }

    private boolean accepts(final DocId docid, final MatchState state) {
        boolean include = true;
        if (this.filter != null) {
            include = this.filter.decide(docid);
        }
        if (include && this.colmax > 0 && this.keymake != null) {
            include = this.collapse(docid, state);
        }
        return include;
    }

    private boolean collapse(final DocId docid, final MatchState state) {
        String key = this.colkey;
        if (key.isEmpty()) {
            key = this.keymake.key(docid);
        }
        final boolean include;
        if (key == null || key.isEmpty()) {
            include = true;
        } else if (key.equals(state.prev())) {
            state.group(state.group() + 1);
            include = state.group() < this.colmax;
        } else {
            state.prev(key);
            state.group(1);
            include = true;
        }
        return include;
    }

    private void order(final List<MSet.Item> found) {
        final Comparator<MSet.Item> order;
        if (this.sorttype == 0) {
            order = this.defaultOrder();
        } else {
            order = this.comparator();
        }
        found.sort(order);
    }

    private Comparator<MSet.Item> defaultOrder() {
        final Comparator<MSet.Item> order;
        if (this.idorder) {
            order = Comparator.comparing(MSet.Item::docId);
        } else {
            order = Comparator.comparingDouble(MSet.Item::weight).reversed();
        }
        return order;
    }

    private Comparator<MSet.Item> comparator() {
        final Comparator<MSet.Item> result;
        if (this.sorttype == EnquireImpl.SLOT) {
            final ValueNo slot = this.sortslot;
            result = (left, right) -> this.compare(
                this.value(left, slot), this.value(right, slot)
            );
        } else {
            final KeyMaker maker = this.keymake;
            result = (left, right) -> this.compare(
                maker.key(left.docId()), maker.key(right.docId())
            );
        }
        return result;
    }

    private String value(final MSet.Item item, final ValueNo slot) {
        return this.shard.openDocument(item.docId(), false)
            .flatMap(document -> document.value(slot))
            .orElse("");
    }

    private int compare(final String left, final String right) {
        final String safe;
        if (left == null) {
            safe = "";
        } else {
            safe = left;
        }
        final String other;
        if (right == null) {
            other = "";
        } else {
            other = right;
        }
        return this.ascending(safe.compareTo(other));
    }

    private int ascending(final int result) {
        final int ordered;
        if (this.sortasc) {
            ordered = result;
        } else {
            ordered = -result;
        }
        return ordered;
    }

    private List<ESet.Item> expand(
        final int max, final ExpandWeight weight, final ExpandDecider chooser
    ) {
        final Weight scheme;
        if (this.chosen == null) {
            scheme = new BoolWeight();
        } else {
            scheme = this.chosen;
        }
        final Map<String, TermCount> freqs = new HashMap<>(8);
        final Map<String, TermCount> docs = new HashMap<>(8);
        final int matching = this.collect(scheme, freqs, docs);
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
        final Weight scheme, final Map<String, TermCount> freqs,
        final Map<String, TermCount> docs
    ) {
        final PostList list = Matcher.resolve(this.shard, this.request, scheme);
        DocId docid = list.next().docId();
        int matching = 0;
        while (!docid.equals(DocId.NONE) && matching < 100) {
            if (this.accepts(docid, new MatchState(this.shard, list))) {
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
