/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DbFlag;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link VidrioOptions}.
 *
 * @since 0.1.0
 */
final class VidrioOptionsTest {

    @Test
    void readsThresholdProperty() {
        Assertions.assertEquals(
            List.of(10_000, 10_000, 12, 999_999_999),
            List.of(
                VidrioOptions.property(""), VidrioOptions.property("0"),
                VidrioOptions.property("12"), VidrioOptions.property("999999999")
            ),
            "Empty and zero mean the default"
        );
    }

    @Test
    void rejectsBadThresholdProperty() {
        for (final String bad : List.of("abc", "-1", "1.5", "1234567890")) {
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> VidrioOptions.property(bad),
                bad
            );
        }
    }

    @Test
    void readsFlagsAndDefaults() {
        final VidrioOptions options = VidrioOptions.of(
            DbFlag.RETRY_LOCK.add(DatabaseAction.CREATE.code())
        );
        Assertions.assertEquals(
            List.of(
                DatabaseAction.CREATE, true, BlockSize.DEFAULT, 10_000,
                BlockSize.MIN, BlockSize.DEFAULT, 3
            ),
            List.of(
                options.action(), options.retry(), options.size(),
                options.threshold(),
                options.withBlockSize(BlockSize.MIN_BYTES).size(),
                options.withBlockSize(7).size(),
                options.withThreshold(3).threshold()
            ),
            "Action, retry, block sizes and thresholds"
        );
    }

    @Test
    void rejectsNonPositiveThreshold() {
        final VidrioOptions options = VidrioOptions.of(0);
        Assertions.assertThrows(
            IllegalArgumentException.class, () -> options.withThreshold(0),
            "Zero"
        );
    }

    @Test
    void readsSystemProperty() {
        final String old = System.getProperty(VidrioOptions.PROPERTY);
        try {
            System.setProperty(VidrioOptions.PROPERTY, "5");
            Assertions.assertEquals(
                5, VidrioOptions.of(0).threshold(), "Property applied"
            );
        } finally {
            if (old == null) {
                System.clearProperty(VidrioOptions.PROPERTY);
            } else {
                System.setProperty(VidrioOptions.PROPERTY, old);
            }
        }
    }
}
