/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the common utilities.
 */
package io.github.andreoss.search4j.core.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Guards}.
 *
 * @since 0.1.0
 */
final class GuardsTest {

    @Test
    void requirePassesWhenTrue() {
        Guards.require(true, "holds");
        Assertions.assertDoesNotThrow(() -> Guards.require(true, "holds"));
    }

    @Test
    void requireThrowsWhenFalse() {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> Guards.require(false, "fails"),
            "False condition rejected"
        );
    }

    @Test
    void notNullReturnsValue() {
        Assertions.assertEquals(
            "v", Guards.notNull("v", "name"), "Non-null kept"
        );
    }

    @Test
    void notNullThrowsForNull() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> Guards.notNull(null, "name"),
            "Null rejected"
        );
    }
}
