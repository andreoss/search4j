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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
    void compressesRepetitiveData() {
        final byte[] data = "aaaa".repeat(100).getBytes(StandardCharsets.UTF_8);
        Assertions.assertTrue(
            Zlib.compress(data).length < data.length, "Smaller output"
        );
    }
}
