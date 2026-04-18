/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher core.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.CollectionStats;
import io.github.andreoss.search4j.core.api.LengthBounds;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.TotalLength;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks that every place ordering children with ties orders them as the
 * reference does, against the sorts captured from its standard library.
 *
 * <p>Positional filters are observed through the order in which they read
 * the position lists of their terms: each term sits at its own offset, so a
 * document matches and every list is read, in checking order.</p>
 *
 * @since 0.11.0
 */
final class OrderSitesTest {

    /**
     * Separator of members in a maximum description.
     */
    private static final Pattern MAX = Pattern.compile(" MAX ");

    @Test
    void ordersIntersectionMembersByAscendingFrequencyAsAHeap() throws IOException {
        Assertions.assertEquals(
            List.of(),
            OrderSitesTest.failures(
                "heap",
                (kids, reads) -> AndSetup.fresh(kids, () -> { }).kids()
            ),
            "Members of an intersection with equal frequencies"
        );
    }

    @Test
    void ordersMaximumMembersByDescendingFrequency() throws IOException {
        Assertions.assertEquals(
            List.of(),
            OrderSitesTest.failures("down", (kids, reads) -> OrderSitesTest.maximum(kids)),
            "Members of a maximum with equal frequencies"
        );
    }

    @Test
    void readsNearTermsByAscendingWdf() throws IOException {
        Assertions.assertEquals(
            List.of(),
            OrderSitesTest.failures(
                "up",
                (kids, reads) -> {
                    new NearPostList(
                        kids.get(0), new TermPos(kids.size() + 1), kids
                    ).testDoc();
                    return OrderSitesTest.named(kids, reads);
                }
            ),
            "Terms of a near filter with equal within-document frequencies"
        );
    }

    @Test
    void readsPhraseTermsByAscendingWdf() throws IOException {
        Assertions.assertEquals(
            List.of(),
            OrderSitesTest.failures(
                "up",
                (kids, reads) -> {
                    new ExactPhrasePostList(kids.get(0), kids).testDoc();
                    return OrderSitesTest.named(kids, reads);
                }
            ),
            "Terms of an exact phrase with equal within-document frequencies"
        );
    }

    private static List<PostList> maximum(final List<PostList> kids) {
        final UnionContext ctx = new UnionContext(
            new Matching(
                new InMemoryShard(),
                new CollectionStats(
                    TermCount.ONE, TermCount.ZERO, new TotalLength(1L), TermCount.ONE,
                    new LengthBounds(TermCount.ONE, TermCount.ONE, TermCount.ONE)
                ),
                () -> { }
            )
        );
        for (final PostList kid : kids) {
            ctx.add(new MatchTree(kid, MatchEstimate.from(kid)), null);
        }
        final List<String> names = new ArrayList<>(kids.size());
        final String text = ctx.maximum().list().description();
        final String inner = text.substring(1, text.length() - 1);
        for (final String name : OrderSitesTest.MAX.split(inner, -1)) {
            names.add(name);
        }
        return OrderSitesTest.named(kids, names);
    }

    private static List<PostList> named(final List<PostList> kids, final List<String> names) {
        final List<PostList> result = new ArrayList<>(names.size());
        for (final String name : names) {
            result.add(kids.get(Integer.parseInt(name.substring(1))));
        }
        return result;
    }

    private static List<String> failures(
        final String kind,
        final BiFunction<List<PostList>, List<String>, List<PostList>> site
    ) throws IOException {
        final List<String> result = new ArrayList<>(0);
        int seen = 0;
        for (final String line : OrderSitesTest.lines()) {
            final String[] row = line.split("\t", -1);
            final String[] keys = row[1].split(",", -1);
            if (kind.equals(row[0]) && keys.length > 1) {
                seen = seen + 1;
                final List<String> reads = new ArrayList<>(keys.length);
                final String order = OrderSitesTest.names(
                    site.apply(OrderSitesTest.kids(reads, keys), reads)
                );
                if (!order.equals(row[2])) {
                    result.add(String.format("%s got %s", line, order));
                }
            }
        }
        if (seen < 150) {
            result.add(String.format("only %d rows of %s", seen, kind));
        }
        return result;
    }

    private static List<PostList> kids(final List<String> reads, final String... keys) {
        final List<PostList> result = new ArrayList<>(keys.length);
        for (int index = 0; index < keys.length; ++index) {
            final long key = Long.parseLong(keys[index]);
            result.add(
                new BooleanPostListFixtureFake(
                    String.format("c%d", index), List.of(DocId.of(1)),
                    new TermCount(key),
                    new DocRange(DocId.of(1), DocId.of(1)), List.of()
                ).wdf(key).positions(index + 1).logging(reads)
            );
        }
        return result;
    }

    private static String names(final List<PostList> kids) {
        final StringBuilder result = new StringBuilder(64);
        for (final PostList kid : kids) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(kid.description().substring(1));
        }
        return result.toString();
    }

    private static List<String> lines() throws IOException {
        try (
            BufferedReader input = new BufferedReader(
                new InputStreamReader(
                    OrderSitesTest.class.getResourceAsStream("sort-vectors.tsv"),
                    StandardCharsets.UTF_8
                )
            )
        ) {
            return input.lines().toList();
        }
    }
}
