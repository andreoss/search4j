/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of the {@link Document} contract through a stub.
 *
 * @since 0.1.0
 */
final class DocumentTest {

    @Test
    void storesAndReadsValue() {
        final StubDocument doc = new StubDocument();
        doc.addValue(new ValueNo(1L), "text");
        Assertions.assertEquals(
            Optional.of("text"), doc.value(new ValueNo(1L)), "Value read"
        );
    }

    @Test
    void emptyValueIsAbsent() {
        Assertions.assertEquals(
            Optional.empty(),
            new StubDocument().value(new ValueNo(1L)),
            "Absent value"
        );
    }

    @Test
    void countsTerms() {
        final StubDocument doc = new StubDocument();
        doc.addTerm("a", new TermCount(3));
        Assertions.assertEquals(
            new TermCount(3), doc.termCount(), "Term count"
        );
    }
}
