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
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Collects the best matches of a search: fills up to the wanted size, then
 * keeps the best seen in a heap whose worst entry raises the minimum weight,
 * counts matching documents, and applies weight and percentage cutoffs.
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
     * Whether weight is the primary key of the order.
     */
    private final boolean first;

    /**
     * Cursor over the matches, asked for matching subquery counts.
     */
    private final MatchCursor cursor;

    /**
     * Matches kept so far.
     */
    private final List<Candidate> results;

    /**
     * Indices of the kept matches, worst first, built once the set is full.
     */
    private final Queue<Integer> heap;

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
            limits, ordering.comparator(limits.ascending()),
            ordering.weightFirst(), cursor, new ArrayList<>(8),
            new Tally(limits.atleast())
        );
    }

    /**
     * Creates a collector with its kept matches and counters.
     *
     * @param limits Fixed settings of the collection
     * @param order Order of the matches, better first
     * @param first Whether weight is the primary key of the order
     * @param cursor Cursor over the matches
     * @param results Kept matches, empty
     * @param tally Counters, none counted
     */
    private Collector(
        final Limits limits, final Comparator<Candidate> order,
        final boolean first, final MatchCursor cursor,
        final List<Candidate> results, final Tally tally
    ) {
        this.limits = limits;
        this.order = order;
        this.first = first;
        this.cursor = cursor;
        this.results = results;
        this.heap = new PriorityQueue<>(new WorstFirst(results, order));
        this.tally = tally;
    }

    double least() {
        return this.tally.least();
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
                this.heap.clear();
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
        return !this.heap.isEmpty()
            && !this.better(item, this.results.get(this.heap.peek()));
    }

    /**
     * Counts a candidate rejected early as matching.
     *
     * @param weight Weight of the candidate
     */
    void reject(final double weight) {
        this.tally.count();
        this.observe(weight);
    }

    /**
     * Offers a candidate.
     *
     * @param item Candidate with its weight
     * @return False when the collection may stop
     */
    boolean process(final Candidate item) {
        this.observe(item.weight());
        this.add(item);
        return !(this.limits.stop() && this.full() && this.enough());
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
     * @param collapse Maximum size of a collapse group, zero for none
     * @return Lower bound, estimate and upper bound
     */
    TermCount[] estimated(final boolean decided, final int collapse) {
        final MatchEstimates found = this.cursor.resolved();
        int lower = found.min().value();
        if (decided) {
            lower = 0;
        }
        if (collapse > 0 && lower > collapse) {
            lower = collapse;
        }
        return new TermCount[] {new TermCount(lower), found.est(), found.max()};
    }

    /**
     * Bounds on the number of matches once matching ends: exact when the set
     * is not full or fewer matches than the minimum to check were found.
     *
     * @param decided Whether a filter can reject documents
     * @return Lower bound, estimate and upper bound
     */
    TermCount[] bounds(final boolean decided) {
        final int[] bounds = new int[3];
        if (!this.full() || this.tally.known() < this.tally.atleast()) {
            int exact = this.tally.known();
            if (!this.full()) {
                exact = this.results.size();
            }
            bounds[0] = exact;
            bounds[1] = exact;
            bounds[2] = exact;
        } else {
            final MatchEstimates found = this.cursor.resolved();
            bounds[0] = found.min().value();
            bounds[1] = found.est().value();
            bounds[2] = found.max().value();
            if (decided) {
                this.filtered(bounds);
            }
            this.adjust(bounds, decided);
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
        if (decided && !percent && known > bounds[0]) {
            bounds[0] = known;
        }
        if (percent) {
            bounds[0] = this.results.size();
            bounds[1] = Math.max(
                (int) (bounds[1] * (1.0 - this.limits.factor()) + 0.5), bounds[0]
            );
        }
        if (decided) {
            bounds[1] = Math.min(Math.max(bounds[1], bounds[0]), bounds[2]);
        } else if (!percent) {
            bounds[0] = Math.max(bounds[0], known);
            bounds[1] = Math.max(bounds[1], known);
        }
    }

    private boolean full() {
        return this.results.size() == this.limits.size();
    }

    private boolean enough() {
        boolean result = this.tally.known() >= this.tally.atleast();
        if (!result && this.tally.known() >= this.limits.size()
            && this.limits.deadline() != 0L
            && System.nanoTime() - this.limits.deadline() > 0L) {
            this.tally.atleast(this.limits.size());
            result = true;
        }
        return result;
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

    private void add(final Candidate item) {
        this.tally.count();
        if (item.weight() >= this.tally.least()) {
            this.observe(item.weight());
            if (this.full()) {
                this.replace(item);
            } else {
                this.results.add(item);
            }
        }
    }

    private void replace(final Candidate item) {
        boolean ready = true;
        if (this.heap.isEmpty()) {
            if (this.tally.pending() && !this.prune(false)) {
                this.results.add(item);
                ready = false;
            }
            ready = ready && !this.results.isEmpty();
            if (ready) {
                for (int idx = 0; idx < this.results.size(); idx = idx + 1) {
                    this.heap.add(idx);
                }
                this.raise();
            }
        }
        if (ready && this.better(item, this.results.get(this.heap.peek()))) {
            final int worst = this.heap.poll();
            this.results.set(worst, item);
            this.heap.add(worst);
            this.raise();
        }
    }

    private void raise() {
        if (this.first && this.enough()) {
            this.tally.least(this.results.get(this.heap.peek()).weight());
        }
    }

    private boolean prune(final boolean finishing) {
        this.tally.pending(false);
        double lowest = Double.POSITIVE_INFINITY;
        final List<Candidate> kept = new ArrayList<>(this.results.size());
        for (final Candidate item : this.results) {
            if (item.weight() >= this.tally.least()) {
                kept.add(item);
                lowest = Math.min(lowest, item.weight());
            }
        }
        if (this.first && this.checked(finishing)) {
            this.tally.least(lowest);
        }
        final int before = this.results.size();
        this.results.clear();
        this.results.addAll(kept);
        return kept.size() == before || finishing;
    }

    private boolean checked(final boolean finishing) {
        final boolean result;
        if (finishing) {
            result = this.tally.known() >= this.tally.atleast();
        } else {
            result = this.enough();
        }
        return result;
    }

    private boolean better(final Candidate item, final Candidate other) {
        return this.order.compare(item, other) < 0;
    }
}
