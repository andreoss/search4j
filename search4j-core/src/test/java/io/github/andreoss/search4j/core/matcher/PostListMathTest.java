/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the matcher core.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.types.DocId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link PostListMath}.
 *
 * @since 0.10.0
 */
final class PostListMathTest {

    @Test
    void countsBothEndsOfARange() {
        Assertions.assertEquals(
            5L,
            PostListMath.length(new DocRange(DocId.of(3), DocId.of(7))),
            "Range length"
        );
    }

    @Test
    void picksTheLowerIdentifier() {
        Assertions.assertEquals(
            DocId.of(2),
            PostListMath.min(DocId.of(9), DocId.of(2)),
            "Lower identifier"
        );
    }
}
