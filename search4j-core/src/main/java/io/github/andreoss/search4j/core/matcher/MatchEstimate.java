/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tree of estimated matcher operations.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves match-count bounds and estimates from boolean query structure.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class MatchEstimate {

    /**
     * Lowest valid document identifier.
     */
    private static final DocId FIRST = DocId.of(1);

    /**
     * Operation represented by this node.
     */
    private final EstimateKind kind;

    /**
     * Child estimate nodes.
     */
    private final List<MatchEstimate> children;

    /**
     * Fixed estimates for a known leaf.
     */
    private final MatchEstimates bounds;

    /**
     * Lowest document identifier in this operation's range.
     */
    private final DocId first;

    /**
     * Highest document identifier in this operation's range.
     */
    private final DocId last;

    private MatchEstimate(
        final EstimateKind operation, final List<MatchEstimate> members,
        final MatchEstimates values, final DocId lower, final DocId upper
    ) {
        this.kind = operation;
        this.children = members;
        this.bounds = values;
        this.first = lower;
        this.last = upper;
    }

    /**
     * Estimate of the matches of a posting list.
     *
     * @param list Posting list
     * @return Match estimate
     */
    public static MatchEstimate from(final PostList list) {
        final MatchEstimate result;
        if (list instanceof AndPostList conjunction) {
            result = conjunction.matchEstimate();
        } else if (list instanceof AndNotPostList difference) {
            result = difference.matchEstimate();
        } else if (list instanceof AndMaybePostList optional) {
            result = optional.matchEstimate();
        } else if (list instanceof OrPostList union) {
            result = union.matchEstimate();
        } else if (list instanceof BoolOrPostList union) {
            result = union.matchEstimate();
        } else if (list instanceof XorPostList exclusive) {
            result = exclusive.matchEstimate();
        } else {
            final DocRange range = list.docIdRange(PostListMath.WHOLE);
            result = MatchEstimate.known(
                new MatchEstimates(
                    list.termFrequency(), list.termFrequency(),
                    list.termFrequency(), range.first(), range.last()
                )
            );
        }
        return result;
    }

    /**
     * Clamps the estimate to a database.
     *
     * @param dbsize Number of documents in the database
     * @param dbfirst First document of the database
     * @param dblast Last document of the database
     * @return Resolved estimates
     */
    public MatchEstimates resolve(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast
    ) {
        if (dbsize.value() <= 0) {
            throw new InvalidArgumentError("Database size must be positive");
        }
        final DocId lower = MatchEstimate.max(
            this.first, Objects.requireNonNull(dbfirst, "database first")
        );
        final DocId upper = MatchEstimate.min(
            this.last, Objects.requireNonNull(dblast, "database last")
        );
        final long capacity = MatchEstimate.length(lower, upper);
        final MatchEstimates calculated = this.calculate(
            dbsize, dbfirst, dblast, lower, upper, capacity
        );
        final long maximum = Math.min(calculated.max().value(), capacity);
        final long minimum = Math.min(calculated.min().value(), maximum);
        return new MatchEstimates(
            MatchEstimate.count(minimum), MatchEstimate.count(
                Math.max(minimum, Math.min(calculated.est().value(), maximum))
            ),
            MatchEstimate.count(maximum), lower, upper
        );
    }

    static MatchEstimate known(
        final TermCount frequency, final DocId first, final DocId last
    ) {
        return MatchEstimate.known(
            new MatchEstimates(frequency, frequency, frequency, first, last)
        );
    }

    static MatchEstimate known(final MatchEstimates values) {
        Objects.requireNonNull(values, "values");
        return new MatchEstimate(
            EstimateKind.KNOWN, List.of(), values,
            values.first(), values.last()
        );
    }

    static MatchEstimate intersection(final List<? extends PostList> lists) {
        return MatchEstimate.combine(EstimateKind.AND, lists);
    }

    static MatchEstimate difference(final PostList left, final PostList right) {
        return MatchEstimate.combine(
            EstimateKind.AND_NOT, List.of(left, right)
        );
    }

    static MatchEstimate union(final List<? extends PostList> lists) {
        return MatchEstimate.combine(EstimateKind.OR, lists);
    }

    static MatchEstimate exclusive(final List<? extends PostList> lists) {
        return MatchEstimate.combine(EstimateKind.XOR, lists);
    }

    private static MatchEstimate combine(
        final EstimateKind operation, final List<? extends PostList> lists
    ) {
        final List<MatchEstimate> children = new ArrayList<>(lists.size());
        for (final PostList list : lists) {
            children.add(MatchEstimate.from(list));
        }
        final DocRange range = MatchEstimate.range(operation, children);
        return new MatchEstimate(
            operation, List.copyOf(children), null,
            range.first(), range.last()
        );
    }

    private static DocRange range(
        final EstimateKind operation, final List<MatchEstimate> children
    ) {
        return switch (operation) {
            case AND -> MatchEstimate.intersectionRange(children);
            case AND_NOT -> new DocRange(
                children.get(0).first, children.get(0).last
            );
            case OR, XOR -> MatchEstimate.unionRange(children);
            case KNOWN -> throw new IllegalArgumentException(
                "A known estimate has no children"
            );
        };
    }

    private static DocRange intersectionRange(
        final List<MatchEstimate> children
    ) {
        DocId lower = MatchEstimate.FIRST;
        DocId upper = DocId.NONE;
        if (!children.isEmpty()) {
            lower = children.get(0).first;
            upper = children.get(0).last;
            for (int index = 1; index < children.size(); ++index) {
                lower = MatchEstimate.max(lower, children.get(index).first);
                upper = MatchEstimate.min(upper, children.get(index).last);
            }
        }
        return new DocRange(lower, upper);
    }

    private static DocRange unionRange(final List<MatchEstimate> children) {
        DocId lower = MatchEstimate.FIRST;
        DocId upper = DocId.NONE;
        boolean found = false;
        for (final MatchEstimate child : children) {
            if (MatchEstimate.length(child.first, child.last) <= 0L) {
                continue;
            }
            if (found) {
                lower = MatchEstimate.min(lower, child.first);
                upper = MatchEstimate.max(upper, child.last);
                continue;
            }
            lower = child.first;
            upper = child.last;
            found = true;
        }
        return new DocRange(lower, upper);
    }

    private MatchEstimates calculate(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final DocId lower, final DocId upper, final long capacity
    ) {
        return switch (this.kind) {
            case KNOWN -> this.bounds;
            case AND -> this.resolveAnd(
                dbsize, dbfirst, dblast, lower, upper, capacity
            );
            case AND_NOT -> this.resolveAndNot(
                dbsize, dbfirst, dblast, lower, upper
            );
            case OR -> this.resolveOr(
                dbsize, dbfirst, dblast, lower, upper, capacity
            );
            case XOR -> this.resolveXor(
                dbsize, dbfirst, dblast, lower, upper, capacity
            );
        };
    }

    private MatchEstimates resolveAnd(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final DocId lower, final DocId upper, final long capacity
    ) {
        long minimum = capacity;
        long maximum = capacity;
        double estimate = capacity;
        for (final MatchEstimate child : this.children) {
            final MatchEstimates part = child.resolve(dbsize, dbfirst, dblast);
            final long range = MatchEstimate.length(
                part.first(), part.last()
            );
            final long outside = Math.max(0L, range - capacity);
            final long overlap = Math.max(0L, part.min().value() - outside);
            if (minimum + overlap <= capacity) {
                minimum = 0L;
            } else {
                minimum += overlap - capacity;
            }
            maximum = Math.min(maximum, part.max().value());
            if (range > 0L) {
                estimate *= (double) part.est().value() / range;
            }
        }
        return MatchEstimate.values(
            minimum, PostListMath.rounded(estimate).value(), maximum,
            lower, upper
        );
    }

    private MatchEstimates resolveAndNot(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final DocId lower, final DocId upper
    ) {
        final MatchEstimates left = this.children.get(0).resolve(
            dbsize, dbfirst, dblast
        );
        final MatchEstimates right = this.children.get(1).resolve(
            dbsize, dbfirst, dblast
        );
        final long overlap = MatchEstimate.length(
            MatchEstimate.max(left.first(), right.first()),
            MatchEstimate.min(left.last(), right.last())
        );
        final long minimum = Math.max(
            0L, left.min().value() - Math.min(overlap, right.max().value())
        );
        final long lrange = MatchEstimate.length(left.first(), left.last());
        final long rrange = MatchEstimate.length(right.first(), right.last());
        final long maximum = Math.min(
            left.max().value(), Math.max(0L, lrange - overlap)
                + Math.max(0L, rrange - right.min().value())
        );
        double estimate = left.est().value();
        if (lrange > 0L && rrange > 0L) {
            estimate *= 1.0 - (double) overlap * right.est().value()
                / ((double) lrange * rrange);
        }
        return MatchEstimate.values(
            minimum, PostListMath.rounded(estimate).value(), maximum,
            lower, upper
        );
    }

    private MatchEstimates resolveOr(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final DocId lower, final DocId upper, final long capacity
    ) {
        long minimum = 0L;
        long maximum = 0L;
        double probability = 0.0;
        final double scale = MatchEstimate.scale(capacity);
        for (final MatchEstimate child : this.children) {
            final MatchEstimates part = child.resolve(dbsize, dbfirst, dblast);
            minimum = Math.max(minimum, part.min().value());
            maximum = Math.min(
                capacity, maximum + (long) part.max().value()
            );
            final double current = part.est().value() * scale;
            probability += current - probability * current;
        }
        return MatchEstimate.values(
            minimum, PostListMath.rounded(probability * capacity).value(),
            maximum, lower, upper
        );
    }

    private MatchEstimates resolveXor(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final DocId lower, final DocId upper, final long capacity
    ) {
        final List<MatchEstimates> parts = this.resolvedChildren(
            dbsize, dbfirst, dblast
        );
        final boolean invert = MatchEstimate.inverted(dbsize, parts);
        final long sum = MatchEstimate.maximumSum(dbsize, parts);
        final boolean exact = MatchEstimate.allExact(dbsize, parts);
        final MatchEstimates result = MatchEstimate.values(
            MatchEstimate.xorMinimum(dbsize, parts, sum, exact),
            PostListMath.rounded(
                MatchEstimate.xorProbability(dbsize, parts, capacity)
                    * capacity
            ).value(),
            MatchEstimate.xorMaximum(dbsize, sum, exact), lower, upper
        );
        MatchEstimates resolved = result;
        if (invert) {
            resolved = MatchEstimate.invert(dbsize, result);
        }
        return resolved;
    }

    private List<MatchEstimates> resolvedChildren(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast
    ) {
        final List<MatchEstimates> parts = new ArrayList<>(this.children.size());
        for (final MatchEstimate child : this.children) {
            parts.add(child.resolve(dbsize, dbfirst, dblast));
        }
        return parts;
    }

    private static boolean inverted(
        final TermCount dbsize, final List<MatchEstimates> parts
    ) {
        boolean result = false;
        for (final MatchEstimates part : parts) {
            if (part.min().equals(dbsize)) {
                result = !result;
            }
        }
        return result;
    }

    private static long maximumSum(
        final TermCount dbsize, final List<MatchEstimates> parts
    ) {
        long result = 0L;
        for (final MatchEstimates part : parts) {
            if (part.min().equals(dbsize)) {
                continue;
            }
            result += part.max().value();
        }
        return result;
    }

    private static boolean allExact(
        final TermCount dbsize, final List<MatchEstimates> parts
    ) {
        boolean result = true;
        for (final MatchEstimates part : parts) {
            if (!part.min().equals(dbsize)) {
                result &= part.max().equals(part.min());
            }
        }
        return result;
    }

    private static double xorProbability(
        final TermCount dbsize, final List<MatchEstimates> parts,
        final long capacity
    ) {
        double result = 0.0;
        final double scale = MatchEstimate.scale(capacity);
        for (final MatchEstimates part : parts) {
            if (part.min().equals(dbsize)) {
                continue;
            }
            final double current = part.est().value() * scale;
            result += current - 2.0 * result * current;
        }
        return result;
    }

    private static long xorMaximum(
        final TermCount dbsize, final long sum, final boolean exact
    ) {
        long result = sum;
        if (sum > dbsize.value()) {
            result = dbsize.value();
            if (exact && (sum & 1L) != (dbsize.value() & 1L)) {
                --result;
            }
        }
        return result;
    }

    private static long xorMinimum(
        final TermCount dbsize, final List<MatchEstimates> parts,
        final long sum, final boolean exact
    ) {
        long result = 0L;
        for (final MatchEstimates part : parts) {
            if (part.min().equals(dbsize)) {
                continue;
            }
            final long remainder = sum - part.max().value();
            if (part.min().value() > remainder) {
                result = Math.max(
                    result, part.min().value() - remainder
                );
            }
        }
        if (exact && result == 0L) {
            result = sum & 1L;
        }
        return result;
    }

    private static MatchEstimates invert(
        final TermCount dbsize, final MatchEstimates values
    ) {
        return new MatchEstimates(
            MatchEstimate.count(dbsize.value() - values.max().value()),
            MatchEstimate.count(dbsize.value() - values.est().value()),
            MatchEstimate.count(dbsize.value() - values.min().value()),
            values.first(), values.last()
        );
    }

    private static double scale(final long capacity) {
        double result = 1.0;
        if (capacity > 0L) {
            result = 1.0 / capacity;
        }
        return result;
    }

    private static MatchEstimates values(
        final long minimum, final long estimate, final long maximum,
        final DocId first, final DocId last
    ) {
        return new MatchEstimates(
            MatchEstimate.count(minimum), MatchEstimate.count(estimate),
            MatchEstimate.count(maximum), first, last
        );
    }

    private static TermCount count(final long value) {
        return new TermCount(
            (int) Math.min(
                Integer.MAX_VALUE, Math.max(0L, value)
            )
        );
    }

    private static long length(final DocId first, final DocId last) {
        return Math.max(0L, (long) last.value() - first.value() + 1L);
    }

    private static DocId max(final DocId left, final DocId right) {
        DocId result = right;
        if (left.compareTo(right) >= 0) {
            result = left;
        }
        return result;
    }

    private static DocId min(final DocId left, final DocId right) {
        DocId result = right;
        if (left.compareTo(right) <= 0) {
            result = left;
        }
        return result;
    }
}
