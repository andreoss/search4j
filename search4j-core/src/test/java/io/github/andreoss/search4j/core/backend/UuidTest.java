/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the backend contract and support types.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Uuid}.
 *
 * @since 0.1.0
 */
final class UuidTest {

    /**
     * Canonical lower-case text form.
     */
    private static final Pattern LOWER = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );

    /**
     * Text form of the sample identifier.
     */
    private static final String SAMPLE = "00112233-4455-6677-8899-aabbccddeeff";

    @Test
    void generatesLowerCaseText() {
        final String text = Uuid.generate().toString();
        Assertions.assertTrue(
            UuidTest.LOWER.matcher(text).matches(),
            String.format("Canonical form: %s", text)
        );
    }

    @Test
    void generatesVersionAndVariant() {
        final byte[] bytes = Uuid.generate().bytes();
        Assertions.assertEquals(
            List.of(0x80, 0x40),
            List.of(bytes[8] & 0xC0, bytes[6] & 0xF0),
            "Variant 10x, version 4"
        );
    }

    @Test
    void generatesNonNil() {
        Assertions.assertFalse(Uuid.generate().nil(), "Not nil");
    }

    @Test
    void generatesDistinctIdentifiers() {
        Assertions.assertNotEquals(
            Uuid.generate(), Uuid.generate(), "Distinct"
        );
    }

    @Test
    void parsesOwnText() {
        final Uuid uuid = Uuid.generate();
        Assertions.assertEquals(
            uuid, Uuid.parse(uuid.toString()), "Round trip"
        );
    }

    @Test
    void parsesUpperCase() {
        Assertions.assertEquals(
            Uuid.parse(UuidTest.SAMPLE),
            Uuid.parse("00112233-4455-6677-8899-AABBCCDDEEFF"),
            "Either case"
        );
    }

    @Test
    void writesBigEndianBytes() {
        final byte[] expected = new byte[Uuid.BYTES];
        for (int index = 0; index < Uuid.BYTES; index += 1) {
            expected[index] = (byte) (index * 0x11);
        }
        Assertions.assertArrayEquals(
            expected, Uuid.parse(UuidTest.SAMPLE).bytes(), "Bytes in order"
        );
    }

    @Test
    void readsBytes() {
        final Uuid uuid = Uuid.parse(UuidTest.SAMPLE);
        Assertions.assertEquals(uuid, Uuid.of(uuid.bytes()), "Round trip");
    }

    @Test
    void writesText() {
        Assertions.assertEquals(
            UuidTest.SAMPLE, Uuid.parse(UuidTest.SAMPLE).toString(), "Text"
        );
    }

    @Test
    void rejectsShortBytes() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Uuid.of(new byte[15]),
            "Fifteen bytes"
        );
    }

    @Test
    void rejectsLongBytes() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Uuid.of(new byte[17]),
            "Seventeen bytes"
        );
    }

    @Test
    void writesNilText() {
        Assertions.assertEquals(
            "00000000-0000-0000-0000-000000000000",
            Uuid.NIL.toString(),
            "Nil text"
        );
    }

    @Test
    void readsNilFromZeroBytes() {
        Assertions.assertTrue(Uuid.of(new byte[Uuid.BYTES]).nil(), "Nil");
    }

    @Test
    void needsBothHalvesZeroForNil() {
        Assertions.assertEquals(
            List.of(false, false),
            List.of(new Uuid(1L, 0L).nil(), new Uuid(0L, 1L).nil()),
            "One half set"
        );
    }

    @Test
    void rejectsMalformedText() {
        for (final String bad : new String[] {
            "",
            "00112233445566778899aabbccddeeff",
            "00112233-4455-6677-8899-aabbccddeef",
            "00112233-4455-6677-8899-aabbccddeeff0",
            "0011223-34455-6677-8899-aabbccddeeff",
            "00112233-4455-6677-8899-aabbccddeegg",
            "{00112233-4455-6677-8899-aabbccddeef}",
        }) {
            Assertions.assertThrows(
                InvalidArgumentError.class,
                () -> Uuid.parse(bad),
                String.format("Rejected: %s", bad)
            );
        }
    }
}
