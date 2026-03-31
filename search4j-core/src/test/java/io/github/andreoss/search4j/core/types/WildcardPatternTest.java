/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the domain value types and constant enumerations.
 */
package io.github.andreoss.search4j.core.types;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link WildcardPattern}.
 *
 * @since 0.1.0
 */
final class WildcardPatternTest {

    @Test
    void carriesPatternBits() {
        Assertions.assertEquals(
            List.of(0, 0x10, 0x20, 0x30),
            List.of(
                WildcardPattern.PREFIX.code(), WildcardPattern.MULTI.code(),
                WildcardPattern.SINGLE.code(), WildcardPattern.GLOB.code()
            ),
            "Pattern bits of the flags word"
        );
    }

    @Test
    void interpretsAsterisk() {
        Assertions.assertEquals(
            List.of(false, true, false, true),
            List.of(
                WildcardPattern.PREFIX.multi(), WildcardPattern.MULTI.multi(),
                WildcardPattern.SINGLE.multi(), WildcardPattern.GLOB.multi()
            ),
            "Asterisk feature"
        );
    }

    @Test
    void interpretsQuestionMark() {
        Assertions.assertEquals(
            List.of(false, false, true, true),
            List.of(
                WildcardPattern.PREFIX.single(), WildcardPattern.MULTI.single(),
                WildcardPattern.SINGLE.single(), WildcardPattern.GLOB.single()
            ),
            "Question mark feature"
        );
    }
}
