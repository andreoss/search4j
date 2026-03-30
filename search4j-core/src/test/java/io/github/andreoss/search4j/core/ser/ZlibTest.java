/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests of {@link Zlib}.
 *
 * @since 0.1.0
 */
final class ZlibTest {

    @Test
    void roundTripsBytes() throws IOException {
        final byte[] data = "hello hello hello".getBytes(StandardCharsets.UTF_8);
        Assertions.assertArrayEquals(
            data, Zlib.decompress(Zlib.compress(data)), "Round trip"
        );
    }

    @Test
    void roundTripsEmptyBytes() throws IOException {
        Assertions.assertEquals(
            0, Zlib.decompress(Zlib.compress(new byte[0])).length,
            "Empty round trip"
        );
    }

    @Test
    void compressesRepetitiveData() {
        final byte[] data = "aaaa".repeat(100).getBytes(StandardCharsets.UTF_8);
        Assertions.assertTrue(
            Zlib.compress(data).length < data.length, "Smaller output"
        );
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsTruncatedInput() {
        final byte[] packed = Zlib.compress(
            "truncated stream ".repeat(500).getBytes(StandardCharsets.UTF_8)
        );
        Assertions.assertThrows(
            IOException.class,
            () -> Zlib.decompress(Arrays.copyOf(packed, packed.length / 2)),
            "Truncated stream"
        );
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsEmptyInput() {
        Assertions.assertThrows(
            IOException.class,
            () -> Zlib.decompress(new byte[0]),
            "Empty stream"
        );
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsInputNeedingDictionary() {
        Assertions.assertThrows(
            IOException.class,
            () -> Zlib.decompress(ZlibTest.withDictionary(), false),
            "Preset dictionary"
        );
    }

    /**
     * Wrapped deflate stream whose header asks for a preset dictionary.
     *
     * @return Stream bytes
     */
    static byte[] withDictionary() {
        return new byte[] {0x78, (byte) 0xBB, 0, 0, 0, 1, 3, 0};
    }
}
