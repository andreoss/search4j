/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Membership of relevance sets, registry lookups and the current entry of
 * a vector term list.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class ContainersTest {

    @Test
    void tracksRelevantDocuments() {
        final RSet set = new RSet();
        Assertions.assertTrue(set.isEmpty(), "new set is empty");
        set.add(DocId.of(7));
        set.add(DocId.of(3));
        Assertions.assertEquals(
            List.of(false, true, false, 2, List.of(DocId.of(3), DocId.of(7))),
            List.of(
                set.isEmpty(), set.contains(DocId.of(3)),
                set.contains(DocId.of(4)), set.size(), set.docIds()
            ),
            "members in ascending order"
        );
        set.clear();
        Assertions.assertEquals(
            List.of(true, false, 0),
            List.of(set.isEmpty(), set.contains(DocId.of(3)), set.size()),
            "cleared set"
        );
    }

    @Test
    void knowsOnlyRegisteredNames() {
        final Registry registry = new Registry();
        Assertions.assertEquals(
            List.of(false, false),
            List.of(
                registry.hasWeightingScheme("nosuchscheme"),
                registry.hasRangeProcessor("nosuchprocessor")
            ),
            "unknown names"
        );
    }

    @Test
    void readsTheCurrentTermOnly() {
        final VectorTermList list = new VectorTermList(List.of("alpha"));
        Assertions.assertThrows(
            InvalidOperationError.class, list::termFrequency, "before the first"
        );
        Assertions.assertThrows(
            InvalidOperationError.class, list::positionCount, "before the first"
        );
        Assertions.assertThrows(
            InvalidOperationError.class, list::wdf, "before the first"
        );
        list.next();
        Assertions.assertEquals(
            List.of(TermCount.ONE, TermCount.ZERO, TermCount.ZERO),
            List.of(list.wdf(), list.termFrequency(), list.positionCount()),
            "statistics of the current term"
        );
    }
}
