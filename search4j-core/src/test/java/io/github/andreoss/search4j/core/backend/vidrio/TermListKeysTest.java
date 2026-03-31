/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.DocId;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermListKeys}.
 *
 * @since 0.1.0
 */
final class TermListKeysTest {

    @Test
    void encodesSortPreservingNumbers() {
        Assertions.assertEquals(
            List.of("0001", "7fff", "808000", "e07fffffff"),
            List.of(
                TermListKeysTest.hex(DocId.of(1)),
                TermListKeysTest.hex(DocId.of(0x7FFF)),
                TermListKeysTest.hex(DocId.of(0x8000)),
                TermListKeysTest.hex(DocId.of(Integer.MAX_VALUE))
            ),
            "Key bytes as the document data keys"
        );
    }

    @Test
    void roundTrips() {
        for (final int number : new int[] {1, 255, 0x8000, Integer.MAX_VALUE}) {
            Assertions.assertEquals(
                DocId.of(number),
                TermListKeys.parse(ItemKey.of(TermListKeys.of(DocId.of(number)))),
                String.format("Document %d", number)
            );
        }
    }

    @Test
    void rejectsCorruptKeys() {
        for (final String hex : new String[] {"", "0000", "000100", "e080000000"}) {
            Assertions.assertThrows(
                DatabaseCorruptError.class,
                () -> TermListKeys.parse(ItemKey.of(HexFormat.of().parseHex(hex))),
                hex
            );
        }
    }

    private static String hex(final DocId doc) {
        return HexFormat.of().formatHex(TermListKeys.of(doc));
    }
}
