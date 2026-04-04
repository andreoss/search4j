/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * In-memory documents and posting helpers for positional matcher tests.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.inmemory.InMemoryShard;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds positioned in-memory documents and reads posting results.
 *
 * @since 0.1.0
 */
final class PositionalTestData {

    private PositionalTestData() {
    }

    static List<Map<String, List<Integer>>> generated() {
        final List<Map<String, List<Integer>>> documents = new ArrayList<>(24);
        for (int number = 1; number <= 24; ++number) {
            documents.add(PositionalTestData.generatedDocument(number));
        }
        return List.copyOf(documents);
    }

    static InMemoryShard shard(
        final List<Map<String, List<Integer>>> documents
    ) {
        final InMemoryShard result = new InMemoryShard();
        for (final Map<String, List<Integer>> terms : documents) {
            final DocumentInternal document = new DocumentInternal();
            PositionalTestData.addTerms(document, terms);
            result.addDocument(document);
        }
        return result;
    }

    static List<String> words(final int count) {
        final List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; ++index) {
            result.add(String.format("t%d", index));
        }
        return List.copyOf(result);
    }

    static List<PostList> terms(
        final InMemoryShard shard, final List<String> words
    ) {
        final List<PostList> result = new ArrayList<>(words.size());
        for (final String word : words) {
            result.add(shard.openPostList(word));
        }
        return result;
    }

    static PostList phrase(
        final InMemoryShard shard, final int window, final String... words
    ) {
        final List<PostList> terms = PositionalTestData.terms(
            shard, List.of(words)
        );
        return new PhrasePostList(
            new AndPostList(terms, () -> { }), new TermPos(window), terms
        );
    }

    static PostList near(
        final InMemoryShard shard, final int window, final String... words
    ) {
        final List<PostList> terms = PositionalTestData.terms(
            shard, List.of(words)
        );
        return new NearPostList(
            new AndPostList(terms, () -> { }), new TermPos(window), terms
        );
    }

    static List<DocId> drain(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        while (!list.atEnd()) {
            list = list.next();
            if (!list.atEnd()) {
                result.add(list.docId());
            }
        }
        return result;
    }

    static List<TermPos> positions(final int... values) {
        final List<TermPos> result = new ArrayList<>(values.length);
        for (final int value : values) {
            result.add(new TermPos(value));
        }
        return result;
    }

    static List<TermPos> read(final PositionList source) {
        final List<TermPos> result = new ArrayList<>(0);
        while (source.next()) {
            result.add(source.position());
        }
        return result;
    }

    private static Map<String, List<Integer>> generatedDocument(
        final int document
    ) {
        final Map<String, List<Integer>> terms = new HashMap<>();
        for (int term = 0; term < 5; ++term) {
            terms.put(
                String.format("t%d", term),
                PositionalTestData.generatedPositions(document, term)
            );
        }
        return Map.copyOf(terms);
    }

    private static List<Integer> generatedPositions(
        final int document, final int term
    ) {
        final List<Integer> positions = new ArrayList<>(4);
        for (int position = 0; position < 9; ++position) {
            final int pattern = (document * 7 + term * 11 + position * 5) % 13;
            if (pattern < 4) {
                positions.add(position);
            }
        }
        return List.copyOf(positions);
    }

    private static void addTerms(
        final DocumentInternal document,
        final Map<String, List<Integer>> terms
    ) {
        for (final Map.Entry<String, List<Integer>> term : terms.entrySet()) {
            if (term.getValue().isEmpty()) {
                document.addTerm(term.getKey(), new TermCount(1));
            } else {
                PositionalTestData.addPositions(
                    document, term.getKey(), term.getValue()
                );
            }
        }
    }

    private static void addPositions(
        final DocumentInternal document,
        final String term,
        final List<Integer> positions
    ) {
        for (final int position : positions) {
            document.addPosting(
                term, new TermPos(position), new TermCount(1)
            );
        }
    }
}
