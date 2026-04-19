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
import java.util.function.Supplier;

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
     * Mask of an unsigned count.
     */
    private static final long UNSIGNED = 0xFFFF_FFFFL;

    /**
     * Operation represented by this node.
     */
    private final EstimateKind kind;

    /**
     * Child estimate nodes.
     */
    private final List<MatchEstimate> children;

    /**
     * Estimates of a known leaf, read when resolving.
     */
    private final Supplier<MatchEstimates> bounds;

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
        final Supplier<MatchEstimates> values, final DocId lower,
        final DocId upper
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
        } else if (list instanceof ExtraWeightPostList extra) {
            result = extra.matchEstimate();
        } else if (list instanceof ValueRangePostList range) {
            result = MatchEstimate.reported(range);
        } else if (list instanceof ValueGePostList range) {
            result = MatchEstimate.known(range.estimates());
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
        final long lower = Math.max(
            this.lowest().value(),
            Objects.requireNonNull(dbfirst, "database first").value()
        );
        final long upper = Math.min(
            this.last.value(),
            Objects.requireNonNull(dblast, "database last").value()
        );
        final long range = MatchEstimate.span(lower, upper);
        final long[] result = this.calculate(dbsize, dbfirst, dblast, range);
        if (range < result[2]) {
            result[2] = range;
            if (range < result[1]) {
                result[1] = range;
            }
        }
        return new MatchEstimates(
            MatchEstimate.count(result[0]), MatchEstimate.count(result[1]),
            MatchEstimate.count(result[2]), new DocId((int) lower),
            new DocId((int) upper)
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
            EstimateKind.KNOWN, List.of(), () -> values,
            values.first(), values.last()
        );
    }

    static MatchEstimate reported(final ValueRangePostList range) {
        final MatchEstimates values = range.estimates();
        return new MatchEstimate(
            EstimateKind.KNOWN, List.of(), range::reported,
            values.first(), values.last()
        );
    }

    static MatchEstimate of(
        final EstimateKind operation, final DocRange range,
        final List<MatchEstimate> members
    ) {
        return new MatchEstimate(
            operation, List.copyOf(members), null, range.first(), range.last()
        );
    }

    static MatchEstimate filtered(
        final EstimateKind operation, final MatchEstimate child,
        final Supplier<MatchEstimates> ratio
    ) {
        return new MatchEstimate(
            operation, List.of(child), ratio,
            PostListMath.WHOLE.first(), PostListMath.WHOLE.last()
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
            case NEAR, PHRASE, EXACT_PHRASE -> PostListMath.WHOLE;
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
            if (child.last.compareTo(child.first) < 0) {
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

    private long[] calculate(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final long range
    ) {
        return switch (this.kind) {
            case KNOWN -> MatchEstimate.counts(this.bounds.get());
            case AND -> this.and(dbsize, dbfirst, dblast, range);
            case AND_NOT -> this.andNot(dbsize, dbfirst, dblast);
            case OR -> this.or(dbsize, dbfirst, dblast, range);
            case XOR -> this.xor(dbsize, dbfirst, dblast, range);
            case NEAR, PHRASE, EXACT_PHRASE -> this.filter(dbsize, dbfirst, dblast);
        };
    }

    private long[] filter(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast
    ) {
        final MatchEstimates part = this.children.get(0).resolve(
            dbsize, dbfirst, dblast
        );
        final MatchEstimates ratio = this.bounds.get();
        final long accepted = ratio.min().value();
        final long rejected = ratio.max().value();
        final long[] result;
        if (accepted == 0L && rejected == 0L) {
            result = new long[] {
                0L, part.est().value() / this.kind.divisor(), part.max().value(),
            };
        } else {
            final long maximum = MatchEstimate.unsigned(part.max().value() - rejected);
            result = new long[] {
                accepted,
                MatchEstimate.clamp(
                    MatchEstimate.round(
                        part.est().value() * ((double) accepted / (accepted + rejected))
                    ),
                    accepted, maximum
                ),
                maximum,
            };
        }
        return result;
    }

    private DocId lowest() {
        DocId result = this.first;
        if (this.kind == EstimateKind.KNOWN) {
            result = this.bounds.get().first();
        }
        return result;
    }

    private static long[] counts(final MatchEstimates values) {
        return new long[] {
            values.min().value(), values.est().value(), values.max().value(),
        };
    }

    private long[] and(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final long range
    ) {
        long minimum = range;
        long maximum = range;
        double estimate = range;
        for (final MatchEstimate child : this.children) {
            final MatchEstimates part = child.resolve(dbsize, dbfirst, dblast);
            final long width = MatchEstimate.span(
                part.first().value(), part.last().value()
            );
            final long outside = MatchEstimate.unsigned(width - range);
            if (outside > part.min().value()) {
                minimum = 0L;
            } else {
                final long sum = minimum + part.min().value() - outside;
                if (sum <= MatchEstimate.UNSIGNED && sum <= range) {
                    minimum = 0L;
                } else {
                    minimum = MatchEstimate.unsigned(sum - range);
                }
            }
            maximum = Math.min(maximum, part.max().value());
            if (width > 0L) {
                estimate = estimate * part.est().value() / width;
            }
        }
        return new long[] {
            minimum,
            MatchEstimate.clamp(MatchEstimate.round(estimate), minimum, maximum),
            maximum,
        };
    }

    private long[] andNot(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast
    ) {
        final MatchEstimates left = this.children.get(0).resolve(
            dbsize, dbfirst, dblast
        );
        final MatchEstimates right = this.children.get(1).resolve(
            dbsize, dbfirst, dblast
        );
        final long overlap = MatchEstimate.span(
            Math.max(left.first().value(), right.first().value()),
            Math.min(left.last().value(), right.last().value())
        );
        final long actual = Math.min(overlap, right.max().value());
        long minimum = 0L;
        if (actual <= left.min().value()) {
            minimum = left.min().value() - actual;
        }
        final long lrange = MatchEstimate.span(
            left.first().value(), left.last().value()
        );
        final long rrange = MatchEstimate.span(
            right.first().value(), right.last().value()
        );
        return new long[] {
            minimum,
            MatchEstimate.round(
                left.est().value() * (
                    1.0 - (double) overlap * right.est().value()
                        / ((double) lrange * rrange)
                )
            ),
            Math.min(
                left.max().value(),
                MatchEstimate.unsigned(
                    lrange - overlap + rrange - right.min().value()
                )
            ),
        };
    }

    private long[] or(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final long range
    ) {
        final MatchEstimates head = this.children.get(0).resolve(
            dbsize, dbfirst, dblast
        );
        long minimum = head.min().value();
        long maximum = head.max().value();
        final double scale = MatchEstimate.scale(range);
        double probability = head.est().value() * scale;
        for (int index = 1; index < this.children.size(); ++index) {
            final MatchEstimates part = this.children.get(index).resolve(
                dbsize, dbfirst, dblast
            );
            minimum = Math.max(minimum, part.min().value());
            if (MatchEstimate.unsigned(range - maximum) <= part.max().value()) {
                maximum = range;
            } else {
                maximum = maximum + part.max().value();
            }
            final double current = part.est().value() * scale;
            probability += current - probability * current;
        }
        return new long[] {
            minimum, MatchEstimate.round(probability * range), maximum,
        };
    }

    private long[] xor(
        final TermCount dbsize, final DocId dbfirst, final DocId dblast,
        final long range
    ) {
        final double scale = MatchEstimate.scale(range);
        final long size = dbsize.value();
        boolean invert = false;
        double probability = 0.0;
        final List<MatchEstimates> parts = new ArrayList<>(this.children.size());
        for (final MatchEstimate child : this.children) {
            final MatchEstimates part = child.resolve(dbsize, dbfirst, dblast);
            if (part.min().value() == size) {
                invert = !invert;
            } else {
                parts.add(part);
                final double current = part.est().value() * scale;
                probability += current - 2.0 * probability * current;
            }
        }
        final long estimate = MatchEstimate.round(probability * range);
        final long maximum = MatchEstimate.xorMaximum(parts, size);
        final long minimum = MatchEstimate.xorMinimum(parts);
        final long[] result;
        if (invert) {
            result = new long[] {size - maximum, size - estimate, size - minimum};
        } else {
            result = new long[] {minimum, estimate, maximum};
        }
        return result;
    }

    private static long xorMaximum(final List<MatchEstimates> parts, final long size) {
        final long sum = MatchEstimate.sum(parts);
        long result = sum;
        if (sum > size) {
            result = size;
            if (MatchEstimate.exact(parts) && (sum & 1L) != (size & 1L)) {
                result = size - 1L;
            }
        }
        return result;
    }

    private static long xorMinimum(final List<MatchEstimates> parts) {
        final long sum = MatchEstimate.sum(parts);
        long result = 0L;
        for (final MatchEstimates part : parts) {
            final long rest = sum - part.max().value();
            if (part.min().value() > rest) {
                result = Math.max(result, part.min().value() - rest);
            }
        }
        if (result == 0L && MatchEstimate.exact(parts)) {
            result = sum & 1L;
        }
        return result;
    }

    private static long sum(final List<MatchEstimates> parts) {
        long result = 0L;
        for (final MatchEstimates part : parts) {
            result += part.max().value();
        }
        return result;
    }

    private static boolean exact(final List<MatchEstimates> parts) {
        boolean result = true;
        for (final MatchEstimates part : parts) {
            result = result && part.max().equals(part.min());
        }
        return result;
    }

    private static long clamp(final long value, final long lower, final long upper) {
        long result = value;
        if (value < lower) {
            result = lower;
        } else if (upper < value) {
            result = upper;
        }
        return result;
    }

    private static long round(final double value) {
        return MatchEstimate.unsigned((long) (value + 0.5));
    }

    private static double scale(final long range) {
        double result = 1.0;
        if (range != 0L) {
            result = 1.0 / range;
        }
        return result;
    }

    private static long span(final long lower, final long upper) {
        return MatchEstimate.unsigned(upper - lower + 1L);
    }

    private static long unsigned(final long value) {
        return value & MatchEstimate.UNSIGNED;
    }

    private static TermCount count(final long value) {
        return TermCount.wrap(value);
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
