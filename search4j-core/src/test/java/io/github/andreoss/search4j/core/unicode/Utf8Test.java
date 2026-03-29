/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the UTF-8 utilities.
 */
package io.github.andreoss.search4j.core.unicode;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Utf8}.
 *
 * @since 0.1.0
 */
final class Utf8Test {

    @Test
    void decodesAscii() {
        Assertions.assertArrayEquals(
            new int[] {97, 98, 99},
            Utf8.codepoints("abc".getBytes(StandardCharsets.UTF_8)),
            "ASCII code points"
        );
    }

    @Test
    void decodesTwoByteSequence() {
        Assertions.assertArrayEquals(
            new int[] {233},
            Utf8.codepoints(
                new byte[] {(byte) 0xC3, (byte) 0xA9}
            ),
            "U+00E9"
        );
    }

    @Test
    void decodesThreeByteSequence() {
        Assertions.assertArrayEquals(
            new int[] {1046},
            Utf8.codepoints(
                new byte[] {(byte) 0xD0, (byte) 0x96}
            ),
            "U+0416"
        );
    }

    @Test
    void decodesFourByteSequence() {
        Assertions.assertArrayEquals(
            new int[] {0x1F600},
            Utf8.codepoints(
                new byte[] {
                    (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80,
                }
            ),
            "U+1F600"
        );
    }

    @Test
    void reportsSingleByteLength() {
        Assertions.assertEquals(
            1, Utf8.length((byte) 0x41), "ASCII lead is one byte"
        );
    }

    @Test
    void reportsFourByteLength() {
        Assertions.assertEquals(
            4, Utf8.length((byte) 0xF0), "Four-byte lead"
        );
    }
}
