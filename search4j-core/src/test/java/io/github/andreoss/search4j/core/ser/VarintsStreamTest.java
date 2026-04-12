/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Stream forms of {@link Varints} against the reference packing: seven bits
 * per byte, least significant first, the high bit marking a continuation.
 *
 * @since 0.1.0
 */
final class VarintsStreamTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void encodesReferenceBytes(final long value, final String hex)
        throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Varints.encode(out, value);
        Assertions.assertEquals(
            hex, HexFormat.of().formatHex(out.toByteArray()), hex
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void packsReferenceBytes(final long value, final String hex) {
        Assertions.assertEquals(
            hex, HexFormat.of().formatHex(Varints.pack(value)), hex
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void decodesReferenceBytes(final long value, final String hex)
        throws IOException {
        Assertions.assertEquals(
            (int) value,
            Varints.decode(
                new ByteArrayInputStream(HexFormat.of().parseHex(hex.concat("7f")))
            ),
            hex
        );
    }

    @Test
    void leavesTheBytesAfterTheInteger() throws IOException {
        final ByteArrayInputStream source = new ByteArrayInputStream(
            HexFormat.of().parseHex("ac0205")
        );
        Varints.decode(source);
        Assertions.assertEquals(5, source.read(), "next byte");
    }

    @Test
    void rejectsAnIntegerCutShort() {
        Assertions.assertThrows(
            IOException.class,
            () -> Varints.decode(
                new ByteArrayInputStream(HexFormat.of().parseHex("8080"))
            ),
            "truncated"
        );
    }

    @Test
    void rejectsAnEmptyStream() {
        Assertions.assertThrows(
            IOException.class,
            () -> Varints.decode(new ByteArrayInputStream(new byte[0])),
            "empty"
        );
    }

    private static Stream<Arguments> vectors() {
        return Stream.of(
            Arguments.of(0L, "00"),
            Arguments.of(1L, "01"),
            Arguments.of(127L, "7f"),
            Arguments.of(128L, "8001"),
            Arguments.of(129L, "8101"),
            Arguments.of(300L, "ac02"),
            Arguments.of(16_383L, "ff7f"),
            Arguments.of(16_384L, "808001"),
            Arguments.of(2_147_483_647L, "ffffffff07"),
            Arguments.of(2_147_483_648L, "8080808008"),
            Arguments.of(4_294_967_295L, "ffffffff0f")
        );
    }
}
