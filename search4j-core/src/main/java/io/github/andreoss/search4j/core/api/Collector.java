/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.matcher.MatchCursor;
import io.github.andreoss.search4j.core.matcher.MatchEstimates;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects the best matches of a search: fills up to the wanted size, then
 * keeps the best seen in a heap whose worst entry raises the minimum weight,
 * collapses matches sharing a key, counts matching documents, and applies
 * weight and percentage cutoffs.
 *
 * @since 0.4.0
 */
final class Collector {

    /**
     * Fixed settings of the collection.
     */
    private final Limits limits;

    /**
     * Order of the matches, better first.
     */
    private final Comparator<Candidate> order;

    /**
     * Primary order of the matches.
     */
    private final Ordering ordering;

    /**
     * Cursor over the matches, asked for matching subquery counts.
     */
    private final MatchCursor cursor;

    /**
     * Matches kept so far.
     */
    private final List<Candidate> results;

    /**
     * Kept matches by position, including positions dropped since.
     */
    private final Slots slots;

    /**
     * Positions of the kept matches in heap layout, worst on top, built once
     * the set is full.
     */
    private final List<Integer> positions;

    /**
     * Heap over the positions.
     */
    private final Heap<Integer> heap;

    /**
     * Collapsing of the matches.
     */
    private final Collapser collapser;

    /**
     * Counters and thresholds of the collection.
     */
    private final Tally tally;

    /**
     * Creates a collector.
     *
     * @param limits Fixed settings of the collection
     * @param ordering Primary order of the matches
     * @param cursor Cursor over the matches
     */
    Collector(
        final Limits limits, final Ordering ordering, final MatchCursor cursor
    ) {
        this(
            limits, ordering, ordering.comparator(limits.ascending(), limits.forward()),
            cursor, new ArrayList<>(8)
        );
    }

    /**
     * Creates a collector with its kept matches.
     *
     * @param limits Fixed settings of the collection
     * @param ordering Primary order of the matches
     * @param order Order of the matches, better first
     * @param cursor Cursor over the matches
     * @param results Kept matches, empty
     */
    private Collector(
        final Limits limits, final Ordering ordering,
        final Comparator<Candidate> order, final MatchCursor cursor,
        final List<Candidate> results
    ) {
        this(limits, ordering, order, cursor, results, new Slots(results));
    }

    /**
     * Creates a collector with its kept matches by position.
     *
     * @param limits Fixed settings of the collection
     * @param ordering Primary order of the matches
     * @param order Order of the matches, better first
     * @param cursor Cursor over the matches
     * @param results Kept matches, empty
     * @param slots Kept matches by position
     */
    private Collector(
        final Limits limits, final Ordering ordering,
        final Comparator<Candidate> order, final MatchCursor cursor,
        final List<Candidate> results, final Slots slots
    ) {
        this(
            limits, ordering, order, cursor, results, slots,
            new Collapser(new Grouping(limits.collapse(), slots, order)),
            new Tally(limits.atleast())
        );
    }

    /**
     * Creates a collector with its collapsing and counters.
     *
     * @param limits Fixed settings of the collection
     * @param ordering Primary order of the matches
     * @param order Order of the matches, better first
     * @param cursor Cursor over the matches
     * @param results Kept matches, empty
     * @param slots Kept matches by position
     * @param collapser Collapsing of the matches
     * @param tally Counters, none counted
     */
    private Collector(
        final Limits limits, final Ordering ordering,
        final Comparator<Candidate> order, final MatchCursor cursor,
        final List<Candidate> results, final Slots slots,
        final Collapser collapser, final Tally tally
    ) {
        this.limits = limits;
        this.order = order;
        this.ordering = ordering;
        this.cursor = cursor;
        this.results = results;
        this.slots = slots;
        this.positions = new ArrayList<>(8);
        this.heap = new Heap<>(this.positions, new Ranks(results, order));
        this.collapser = collapser;
        this.tally = tally;
    }

    double least() {
        return this.tally.least();
    }

    /**
     * Whether matches are collapsed, so each needs its collapse key.
     *
     * @return True when collapsing
     */
    boolean collapsing() {
        return this.limits.collapse() > 0;
    }

    /**
     * Raises the minimum weight a match needs.
     *
     * @param weight New minimum, ignored unless higher
     */
    void minimum(final double weight) {
        if (weight > this.tally.least()) {
            this.tally.least(weight);
            if (!this.results.isEmpty()) {
                this.positions.clear();
                this.tally.pending(true);
            }
        }
    }

    /**
     * Counts a decision of the filter.
     *
     * @param accept Whether the filter accepted the document
     */
    void decide(final boolean accept) {
        this.tally.decide(accept);
    }

    /**
     * Whether a candidate ranks no better than the worst kept match.
     *
     * @param item Candidate with its sort key
     * @return True when the set is full and the candidate cannot enter it
     */
    boolean worse(final Candidate item) {
        return !this.positions.isEmpty()
            && !this.better(item, this.results.get(this.positions.get(0)));
    }

    /**
     * Whether a candidate that cannot enter the set is rejected early: always
     * without collapsing, and once enough matches were counted with it.
     *
     * @return True when the candidate is rejected early
     */
    boolean early() {
        return !this.collapsing() || this.checked(false);
    }

    /**
     * Counts a candidate rejected early as matching, unless collapsing.
     *
     * @param weight Weight of the candidate
     */
    void reject(final double weight) {
        if (!this.collapsing()) {
            this.tally.count();
        }
        this.observe(weight);
    }

    /**
     * Offers a candidate.
     *
     * @param item Candidate with its weight and collapse key
     * @return False when the collection may stop
     */
    boolean process(final Candidate item) {
        this.observe(item.weight());
        if (this.collapsing()) {
            this.collapse(item);
        } else {
            this.add(item);
        }
        return !(this.limits.forward() && this.ordering.stops() && this.full()
            && this.checked(false));
    }

    /**
     * Applies the percentage cutoff against the best weight once matching
     * ends.
     */
    void finish() {
        if (!this.results.isEmpty() && this.tally.best() != 0.0
            && this.limits.percent() != 0) {
            this.minimum(
                this.limits.factor() / (
                    this.tally.matched() / (double) this.cursor.subqueries()
                        / this.tally.best()
                )
            );
            if (this.tally.pending()) {
                this.prune(true);
            }
        }
    }

    /**
     * Sorted matches from a rank on.
     *
     * @param from Number of best matches to skip
     * @param none Whether no matches were asked for
     * @param shard Database the documents belong to
     * @return Matches in order, ranked from one after the skipped ones
     */
    List<MSet.Item> items(final int from, final boolean none, final Shard shard) {
        final List<Candidate> sorted = new ArrayList<>(this.results);
        sorted.sort(this.order);
        final List<MSet.Item> items = new ArrayList<>(sorted.size());
        if (from <= sorted.size() && !(from == 0 && none)) {
            for (final Candidate item : sorted.subList(from, sorted.size())) {
                items.add(
                    new MSetItemImpl(
                        item.docId(), item.weight(), from + items.size() + 1, shard
                    )
                );
            }
        }
        return items;
    }

    /**
     * Bounds on the number of matches before any document is checked.
     *
     * @param decided Whether a filter can reject documents
     * @return Lower bound, estimate and upper bound
     */
    TermCount[] estimated(final boolean decided) {
        final MatchEstimates found = this.cursor.resolved();
        int lower = found.min().value();
        if (decided) {
            lower = 0;
        }
        if (this.collapsing() && lower > this.limits.collapse()) {
            lower = this.limits.collapse();
        }
        return new TermCount[] {new TermCount(lower), found.est(), found.max()};
    }

    /**
     * Bounds on the number of matches once matching ends: exact when the set
     * is not full, or, without collapsing, when fewer matches than the
     * minimum to check were found.
     *
     * @param decided Whether a filter can reject documents
     * @return Lower bound, estimate and upper bound
     */
    TermCount[] bounds(final boolean decided) {
        final int[] bounds = new int[3];
        if (!this.collapsing()
            && (!this.full() || this.tally.known() < this.tally.atleast())) {
            int exact = this.tally.known();
            if (!this.full()) {
                exact = this.results.size();
            }
            bounds[0] = exact;
            bounds[1] = exact;
            bounds[2] = exact;
        } else if (this.full()) {
            final MatchEstimates found = this.cursor.resolved();
            bounds[0] = found.min().value();
            bounds[1] = found.est().value();
            bounds[2] = found.max().value();
            if (decided) {
                this.filtered(bounds);
            }
            this.adjust(bounds, decided);
        } else {
            bounds[0] = this.results.size();
            bounds[1] = bounds[0];
            bounds[2] = bounds[0];
        }
        return new TermCount[] {
            new TermCount(bounds[0]), new TermCount(bounds[1]),
            new TermCount(bounds[2]),
        };
    }

    private void filtered(final int... bounds) {
        final int accepted = this.tally.accepted();
        final int rejected = this.tally.rejected();
        if (accepted == 0 && rejected == 0) {
            bounds[0] = 0;
        } else {
            bounds[0] = accepted;
            bounds[2] = bounds[2] - rejected;
            bounds[1] = Math.min(
                Math.max(
                    (int) (bounds[1] * ((double) accepted / (accepted + rejected))
                        + 0.5),
                    bounds[0]
                ),
                bounds[2]
            );
        }
    }

    private void adjust(final int[] bounds, final boolean decided) {
        final int known = this.tally.known();
        final boolean percent = this.limits.percent() != 0;
        final boolean collapsing = this.collapsing();
        final double unique = this.collapsed(bounds);
        double scale = 1.0;
        if (decided && !percent && !collapsing && known > bounds[0]) {
            bounds[0] = known;
        }
        if (percent) {
            scale = 1.0 - this.limits.factor();
            bounds[0] = this.results.size();
        }
        scale = scale * unique;
        if (scale != 1.0) {
            bounds[1] = Math.max((int) (bounds[1] * scale + 0.5), bounds[0]);
        }
        if (collapsing || decided) {
            bounds[1] = Math.min(Math.max(bounds[1], bounds[0]), bounds[2]);
        } else if (!percent) {
            bounds[0] = Math.max(bounds[0], known);
            bounds[1] = Math.max(bounds[1], known);
        }
    }

    private double collapsed(final int... bounds) {
        double unique = 1.0;
        if (this.collapsing()) {
            bounds[0] = this.collapser.lower();
            final int considered = this.collapser.considered();
            if (considered > 0) {
                unique = (considered - this.collapser.ignored()) / (double) considered;
            }
            bounds[2] = bounds[2] - this.collapser.ignored();
        }
        return unique;
    }

    private boolean full() {
        return this.results.size() == this.limits.size();
    }

    private boolean checked(final boolean finishing) {
        boolean result = this.tally.known() >= this.tally.atleast();
        if (!result && !finishing && this.expired()) {
            this.tally.atleast(this.limits.size());
            result = true;
        }
        return result;
    }

    private boolean expired() {
        return this.tally.known() >= this.limits.size() && this.limits.deadline() != 0L
            && System.nanoTime() - this.limits.deadline() > 0L;
    }

    private void collapse(final Candidate item) {
        final Collapse action = this.collapser.check(item);
        if (action == Collapse.REPLACE) {
            this.replace(this.collapser.evicted(), item);
        } else if (action != Collapse.REJECT) {
            this.record(action, this.add(item));
        }
    }

    private void record(final Collapse action, final int index) {
        if (action != Collapse.EMPTY && index >= 0) {
            this.collapser.process(action, index);
        }
    }

    private void observe(final double weight) {
        if (weight > this.tally.best()) {
            this.tally.best(weight);
            if (this.limits.percent() != 0) {
                this.tally.matched(this.cursor.matchingSubqueries().value());
                this.minimum(weight * this.limits.factor());
            }
        }
    }

    private int add(final Candidate item) {
        this.tally.count();
        int index = -1;
        if (item.weight() >= this.tally.least()) {
            this.observe(item.weight());
            if (this.full()) {
                index = this.enter(item);
            } else {
                this.results.add(item);
                index = this.results.size() - 1;
            }
        }
        return index;
    }

    private int enter(final Candidate item) {
        int index = -1;
        boolean ready = true;
        if (this.positions.isEmpty()) {
            if (this.tally.pending() && !this.prune(false)) {
                this.results.add(item);
                index = this.results.size() - 1;
                ready = false;
            }
            ready = ready && !this.results.isEmpty();
            if (ready) {
                for (int idx = 0; idx < this.results.size(); idx = idx + 1) {
                    this.positions.add(idx);
                }
                this.heap.make();
                this.raise();
            }
        }
        if (ready && this.better(item, this.results.get(this.positions.get(0)))) {
            index = this.positions.get(0);
            this.results.set(index, item);
            this.heap.replace();
            this.raise();
        }
        return index;
    }

    private void replace(final int index, final Candidate item) {
        if (this.slots.put(index, item) && !this.positions.isEmpty()) {
            this.heap.siftdown(index);
        }
    }

    private void raise() {
        if (this.ordering.weightFirst() && this.checked(false)) {
            this.tally.least(this.results.get(this.positions.get(0)).weight());
        }
    }

    private boolean prune(final boolean finishing) {
        this.tally.pending(false);
        double lowest = Double.POSITIVE_INFINITY;
        int kept = 0;
        final int before = this.results.size();
        for (int idx = 0; idx < before; idx = idx + 1) {
            final Candidate item = this.results.get(idx);
            if (item.weight() >= this.tally.least()) {
                this.move(idx, kept, item);
                lowest = Math.min(lowest, item.weight());
                kept = kept + 1;
            }
        }
        if (this.ordering.weightFirst() && this.checked(finishing)) {
            this.tally.least(lowest);
        }
        this.slots.truncate(kept);
        return kept == before || finishing;
    }

    private void move(final int from, final int target, final Candidate item) {
        if (from != target) {
            this.results.set(target, item);
            if (this.collapsing()) {
                this.collapser.moved(from, target);
            }
        }
    }

    private boolean better(final Candidate item, final Candidate other) {
        return this.order.compare(item, other) < 0;
    }
}
