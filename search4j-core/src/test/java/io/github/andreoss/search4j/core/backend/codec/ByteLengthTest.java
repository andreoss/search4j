/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the byte encodings of string lists.
 */
package io.github.andreoss.search4j.core.backend.codec;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ByteLengthWriter} and {@link ByteLengthReader}.
 *
 * @since 0.1.0
 */
final class ByteLengthTest {

    @Test
    void encodesLengthThenBytes() {
        final ByteLengthWriter writer = new ByteLengthWriter();
        writer.append(ByteLengthTest.utf("ab"));
        writer.append(new byte[0]);
        writer.append(ByteLengthTest.utf("c"));
        Assertions.assertArrayEquals(
            new byte[] {'b', 'a', 'b', '`', 'a', 'c'},
            writer.bytes(),
            "Masked lengths"
        );
    }

    @Test
    void roundTripsStrings() {
        final List<String> items = List.of(
            "", "z", "same", "same", "y".repeat(255)
        );
        final ByteLengthWriter writer = new ByteLengthWriter();
        for (final String item : items) {
            writer.append(ByteLengthTest.utf(item));
        }
        final ByteLengthReader reader = new ByteLengthReader(writer.bytes());
        final List<String> read = new ArrayList<>(0);
        while (reader.hasNext()) {
            read.add(new String(reader.next(), StandardCharsets.UTF_8));
        }
        Assertions.assertEquals(items, read, "Strings read back");
    }

    @Test
    void rejectsTooLongString() {
        final ByteLengthWriter writer = new ByteLengthWriter();
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> writer.append(new byte[256]),
            "String over 255 bytes"
        );
    }

    @Test
    void keepsReaderInputIsolated() {
        final byte[] data = {'a', 'a'};
        final ByteLengthReader reader = new ByteLengthReader(data);
        data[1] = 'z';
        Assertions.assertArrayEquals(
            ByteLengthTest.utf("a"), reader.next(), "Input copied"
        );
    }

    @Test
    void readsEmptyString() {
        Assertions.assertArrayEquals(
            new byte[0],
            new ByteLengthReader(new byte[] {'`'}).next(),
            "Empty item"
        );
    }

    @Test
    void endsAfterLastString() {
        final ByteLengthReader reader = new ByteLengthReader(new byte[] {'`'});
        reader.next();
        Assertions.assertFalse(reader.hasNext(), "At end");
    }

    @Test
    void rejectsReadPastEnd() {
        Assertions.assertThrows(
            NoSuchElementException.class,
            new ByteLengthReader(new byte[0])::next,
            "Read past end"
        );
    }

    @Test
    void rejectsTruncatedString() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            new ByteLengthReader(new byte[] {'c', 'a', 'b'})::next,
            "String cut short"
        );
    }

    @Test
    void acceptsStringEndingInput() {
        Assertions.assertArrayEquals(
            ByteLengthTest.utf("ab"),
            new ByteLengthReader(new byte[] {'b', 'a', 'b'}).next(),
            "String up to the end"
        );
    }

    @Test
    void decodesHighLengthByte() {
        final byte[] data = new byte[256];
        data[0] = (byte) 159;
        Assertions.assertEquals(
            255, new ByteLengthReader(data).next().length, "Longest string"
        );
    }

    @Test
    void rejectsNegativeLength() {
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> Lengths.encode(-1),
            "Negative length"
        );
    }

    private static byte[] utf(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
