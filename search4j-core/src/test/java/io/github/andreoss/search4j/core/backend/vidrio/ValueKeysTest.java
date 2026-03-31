/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ValueKeys}: keys as the reference packs them, and the
 * chunk start taken from a key.
 *
 * @since 0.1.0
 */
final class ValueKeysTest {

    /**
     * Slot zero.
     */
    private static final ValueNo ZERO = new ValueNo(0L);

    @Test
    void packsKeysAsReference() {
        final List<String> seen = new ArrayList<>(10);
        seen.add(ValueKeysTest.hex(ValueKeys.chunk(ValueKeysTest.ZERO, DocId.of(1))));
        seen.add(
            ValueKeysTest.hex(ValueKeys.chunk(new ValueNo(300L), DocId.of(40_000)))
        );
        seen.add(
            ValueKeysTest.hex(ValueKeys.chunk(new ValueNo(ValueNo.BAD), DocId.of(1)))
        );
        seen.add(ValueKeysTest.hex(ValueKeys.stats(ValueKeysTest.ZERO)));
        seen.add(ValueKeysTest.hex(ValueKeys.stats(new ValueNo(1L))));
        seen.add(ValueKeysTest.hex(ValueKeys.stats(new ValueNo(256L))));
        seen.add(ValueKeysTest.hex(ValueKeys.stats(new ValueNo(0x12345L))));
        seen.add(ValueKeysTest.hex(ValueKeys.stats(new ValueNo(ValueNo.BAD))));
        seen.add(ValueKeysTest.hex(ValueKeys.slots(DocId.of(1))));
        seen.add(ValueKeysTest.hex(ValueKeys.slots(DocId.of(40_000))));
        Assertions.assertEquals(
            List.of(
                "00d8000001", "00d8ac02809c40", "00d8ffffffff0f0001",
                "00d0", "00d001", "00d00001", "00d0452301", "00d0ffffffff",
                "000100", "809c4000"
            ),
            seen,
            "Chunk, statistics and slots keys as the reference packs them"
        );
    }

    @Test
    void takesChunkStartFromKey() {
        final List<Optional<DocId>> seen = new ArrayList<>(8);
        seen.add(ValueKeysTest.start(0L, "00d8000001"));
        seen.add(ValueKeysTest.start(300L, "00d8ac02809c40"));
        seen.add(ValueKeysTest.start(0L, "00d8ac02809c40"));
        seen.add(ValueKeysTest.start(0L, "00d0"));
        seen.add(ValueKeysTest.start(0L, ""));
        seen.add(ValueKeysTest.start(0L, "00"));
        seen.add(ValueKeysTest.start(0L, "01d8000001"));
        seen.add(ValueKeysTest.start(0L, "00d9000001"));
        Assertions.assertEquals(
            List.of(
                Optional.of(DocId.of(1)), Optional.of(DocId.of(40_000)),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty()
            ),
            seen,
            "Start of a chunk of the slot, empty for other keys"
        );
    }

    @Test
    void refusesDamagedChunkKeys() {
        final List<String> seen = new ArrayList<>(6);
        final String wide = String.format(
            "00d800%s", HexFormat.of().formatHex(SortableUints.pack(1L << 31))
        );
        final List<String> keys = List.of(
            "00d880", "00d800", "00d80000", "00d8000000", "00d800000100",
            "00d800ff", wide
        );
        for (final String key : keys) {
            seen.add(
                Assertions.assertThrows(
                    DatabaseCorruptError.class,
                    () -> ValueKeysTest.start(0L, key),
                    key
                ).getMessage()
            );
        }
        Assertions.assertEquals(
            List.of(
                "Value chunk key slot out of data",
                "Bad document number in value key 00d800",
                "Bad document number in value key 00d80000",
                "Bad value key 00d8000000",
                "Bad value key 00d800000100",
                "Bad document number in value key 00d800ff",
                String.format("Bad value key %s", wide)
            ),
            seen,
            "Truncated slot, truncated or absent document, zero, trailing bytes, bad lead byte, wide"
        );
    }

    private static Optional<DocId> start(final long slot, final String key) {
        return ValueKeys.chunkStart(
            new ValueNo(slot), ItemKey.of(HexFormat.of().parseHex(key))
        );
    }

    private static String hex(final byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
