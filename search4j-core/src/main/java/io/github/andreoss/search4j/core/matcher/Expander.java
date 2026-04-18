/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.WildcardLimit;
import io.github.andreoss.search4j.core.unicode.Utf8;
import java.nio.charset.StandardCharsets;

/**
 * Expansion of a wildcard or edit distance subquery over the terms of the
 * shard: terms are taken in order, a leading wildcard skips the terms of a
 * capital-letter prefix, and the most frequent are kept when the limit asks
 * for them.
 *
 * @since 0.10.0
 */
final class Expander {

    /**
     * Shard, statistics and callback of the search.
     */
    private final Matching env;

    /**
     * Posting lists of the leaves.
     */
    private final Leaves leaves;

    /**
     * Terms the subquery expands to.
     */
    private final ExpansionSpec spec;

    /**
     * Whether terms of a capital-letter prefix may still be skipped.
     */
    private boolean upper;

    Expander(final Matching env, final Leaves leaves, final ExpansionSpec spec) {
        this.env = env;
        this.leaves = leaves;
        this.spec = spec;
    }

    ExpansionSpec spec() {
        return this.spec;
    }

    void into(final UnionContext ctx, final double factor, final TermFreqs sink) {
        final byte[] fixed = this.spec.fixed();
        final Budget budget = Budget.of(this.spec);
        this.upper = fixed.length == 0;
        TermList list = this.env.shard().openAllTerms(Expander.whole(fixed)).next();
        while (budget.open() && !list.atEnd()) {
            list = this.visit(list, ctx, budget, factor);
        }
        if (this.spec.limits().limit() == WildcardLimit.MOST_FREQUENT) {
            ctx.frequent((int) this.spec.limits().max().value());
        }
        if (sink != null) {
            this.register(ctx, sink);
        }
    }

    private TermList visit(
        final TermList list, final UnionContext ctx, final Budget budget, final double factor
    ) {
        final byte[] bytes = list.term().getBytes(StandardCharsets.UTF_8);
        final boolean inside = Expander.starts(bytes, this.spec.fixed());
        final TermList result;
        if (inside && this.upper && bytes[0] >= 'A' && bytes[0] <= 'Z') {
            this.upper = false;
            result = list.skipTo("[");
        } else if (inside) {
            this.upper = this.upper && bytes[0] < 'A';
            if (this.spec.test(bytes) && budget.take()) {
                ctx.add(this.leaves.expansion(list.term(), factor), null);
            }
            result = list.next();
        } else {
            result = list.next();
        }
        return result;
    }

    private void register(final UnionContext ctx, final TermFreqs sink) {
        for (final PostList term : ctx.lists()) {
            final TermFrequencies stats = this.env.shard().frequencies(
                ((LeafPostList) term).term()
            );
            sink.assign(stats.termFrequency().value(), 0L, stats.collection().value());
            ctx.freqs(sink);
        }
    }

    private static String whole(final byte[] fixed) {
        int lead = fixed.length - 1;
        while (lead > 0 && (fixed[lead] & 0xC0) == 0x80) {
            lead = lead - 1;
        }
        int end = fixed.length;
        if (lead >= 0 && (fixed[lead] & 0x80) != 0
            && lead + Utf8.length(fixed[lead]) > fixed.length) {
            end = lead;
        }
        return new String(fixed, 0, end, StandardCharsets.UTF_8);
    }

    private static boolean starts(final byte[] term, final byte[] fixed) {
        boolean result = term.length >= fixed.length;
        for (int index = 0; result && index < fixed.length; ++index) {
            result = term[index] == fixed[index];
        }
        return result;
    }
}
