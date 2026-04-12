/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the in-memory database backend.
 */
package io.github.andreoss.search4j.core.backend.inmemory;

import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link InMemoryShard} through the shard contract.
 *
 * @since 0.1.0
 */
final class InMemoryShardTest {

    @Test
    void opensEveryDocumentAsLeaf() {
        final LeafPostList list = InMemoryShardTest.filled()
            .openLeafPostList("", false).orElseThrow();
        Assertions.assertEquals(
            List.of(
                new TermCount(3), new TermCount(3), new TermCount(1),
                "AllDocsPostList(3)"
            ),
            List.of(
                list.termFrequency(), list.collectionFrequency(),
                list.wdfUpperBound(), list.description()
            ),
            "Every document, frequency one"
        );
    }

    @Test
    void opensTermAsLeaf() {
        final InMemoryShard shard = InMemoryShardTest.filled();
        final TermFrequencies freqs = shard.frequencies("apple");
        final LeafPostList list = shard.openLeafPostList("apple", true)
            .orElseThrow();
        Assertions.assertEquals(
            List.of(
                freqs.termFrequency(), freqs.collection(),
                "InMemoryPostList(apple)"
            ),
            List.of(
                list.termFrequency(), list.collectionFrequency(),
                list.description()
            ),
            "Term statistics"
        );
    }

    @Test
    void boundsLeafWdfByFirstPostingAndTheRest() {
        final InMemoryShard shard = new InMemoryShard();
        final List<TermCount> seen = new ArrayList<>(2);
        for (final int first : new int[] {2, 6}) {
            final DocumentInternal one = new DocumentInternal();
            one.addTerm("pear", new TermCount(first));
            shard.replaceDocument(DocId.of(1), one);
            final DocumentInternal two = new DocumentInternal();
            two.addTerm("pear", new TermCount(5));
            shard.replaceDocument(DocId.of(2), two);
            seen.add(shard.openLeafPostList("pear", false).orElseThrow().wdfUpperBound());
        }
        Assertions.assertEquals(
            List.of(new TermCount(5), new TermCount(6)), seen,
            "Larger of the first frequency and the collection rest"
        );
    }

    @Test
    void opensNoLeafForUnknownTerm() {
        Assertions.assertTrue(
            InMemoryShardTest.filled().openLeafPostList("absent", true)
                .isEmpty(),
            "Unknown term"
        );
    }

    @Test
    void readsLeafPositions() {
        final LeafPostList list = InMemoryShardTest.filled()
            .openLeafPostList("apple", true).orElseThrow();
        list.next();
        final PositionList positions = list.readPositionList();
        final List<TermPos> seen = new ArrayList<>(0);
        while (positions.next()) {
            seen.add(positions.position());
        }
        Assertions.assertEquals(
            List.of(new TermPos(1), new TermPos(5)), seen, "First document"
        );
    }

    @Test
    void opensTermListDirectly() {
        Assertions.assertEquals(
            "apple",
            InMemoryShardTest.filled().openTermListDirect(new DocId(1))
                .next().term(),
            "First term"
        );
    }

    @Test
    void describesItself() {
        final InMemoryShard shard = new InMemoryShard();
        Assertions.assertEquals(
            List.of(new TermCount(1), Backend.IN_MEMORY, "InMemoryShard"),
            List.of(shard.size(), shard.backend(), shard.description()),
            "One in-memory shard"
        );
    }

    @Test
    void opensNoMissingDocumentLazily() {
        Assertions.assertTrue(
            InMemoryShardTest.filled().openDocument(new DocId(9), true)
                .isEmpty(),
            "Missing"
        );
    }

    @Test
    void rejectsMissingDocumentEagerly() {
        final InMemoryShard shard = InMemoryShardTest.filled();
        Assertions.assertThrows(
            DocNotFoundError.class,
            () -> shard.openDocument(new DocId(9), false),
            "Missing"
        );
    }

    @Test
    void checksValueBySkipping() {
        final ValueList list = InMemoryShardTest.filled()
            .openValueList(new ValueNo(1L));
        Assertions.assertEquals(
            "true 2=a",
            String.format(
                "%b %s=%s", list.check(new DocId(2)), list.docId(), list.value()
            ),
            "Moved to the holder"
        );
    }

    private static InMemoryShard filled() {
        final InMemoryShard shard = new InMemoryShard();
        shard.addDocument(Corpus.first());
        shard.addDocument(Corpus.second());
        shard.addDocument(Corpus.third());
        return shard;
    }
}
