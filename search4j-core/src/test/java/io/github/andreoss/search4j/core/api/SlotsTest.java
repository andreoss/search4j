/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Slots}.
 *
 * @since 0.10.0
 */
final class SlotsTest {

    @Test
    void keepsAMatchPastTheEndAside() {
        final List<Candidate> live = new ArrayList<>(1);
        live.add(new Candidate(DocId.of(1), 1.0));
        final Slots slots = new Slots(live);
        final Candidate item = new Candidate(DocId.of(2), 0.5);
        Assertions.assertAll(
            () -> Assertions.assertFalse(slots.put(3, item), "Past the end"),
            () -> Assertions.assertSame(item, slots.apply(3), "Kept aside"),
            () -> Assertions.assertEquals(1, live.size(), "Kept unchanged")
        );
    }

    @Test
    void replacesAKeptMatch() {
        final List<Candidate> live = new ArrayList<>(1);
        live.add(new Candidate(DocId.of(1), 1.0));
        final Slots slots = new Slots(live);
        final Candidate item = new Candidate(DocId.of(2), 0.5);
        Assertions.assertAll(
            () -> Assertions.assertTrue(slots.put(0, item), "Inside"),
            () -> Assertions.assertSame(item, live.get(0), "Replaced")
        );
    }
}
