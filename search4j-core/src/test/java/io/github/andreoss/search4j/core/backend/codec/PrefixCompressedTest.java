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
 * Tests of {@link PrefixCompressedWriter} and {@link PrefixCompressedReader}.
 *
 * @since 0.1.0
 */
final class PrefixCompressedTest {

    @Test
    void encodesFirstWordWithLength() {
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        writer.append(PrefixCompressedTest.utf("abc"));
        Assertions.assertArrayEquals(
            new byte[] {PrefixCompressedTest.len(3), 'a', 'b', 'c'},
            writer.bytes(),
            "First word"
        );
    }

    @Test
    void encodesSharedPrefix() {
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        writer.append(PrefixCompressedTest.utf("abc"));
        writer.append(PrefixCompressedTest.utf("abde"));
        writer.append(PrefixCompressedTest.utf("b"));
        Assertions.assertArrayEquals(
            new byte[] {
                PrefixCompressedTest.len(3), 'a', 'b', 'c',
                PrefixCompressedTest.len(2), PrefixCompressedTest.len(2),
                'd', 'e',
                PrefixCompressedTest.len(0), PrefixCompressedTest.len(1), 'b',
            },
            writer.bytes(),
            "Prefix kept, rest added"
        );
    }

    @Test
    void encodesRepeatedWord() {
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        writer.append(PrefixCompressedTest.utf("ab"));
        writer.append(PrefixCompressedTest.utf("ab"));
        Assertions.assertArrayEquals(
            new byte[] {
                PrefixCompressedTest.len(2), 'a', 'b',
                PrefixCompressedTest.len(2), PrefixCompressedTest.len(0),
            },
            writer.bytes(),
            "Whole word kept"
        );
    }

    @Test
    void encodesNothing() {
        Assertions.assertEquals(
            0, new PrefixCompressedWriter().bytes().length, "Empty"
        );
    }

    @Test
    void roundTripsWords() {
        final List<String> words = List.of(
            "a", "ab", "abc", "abd", "b", "bcdefg", "bcz", "été"
        );
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        for (final String word : words) {
            writer.append(PrefixCompressedTest.utf(word));
        }
        Assertions.assertEquals(
            words,
            PrefixCompressedTest.read(writer.bytes()),
            "Words read back"
        );
    }

    @Test
    void roundTripsLongWord() {
        final String word = "x".repeat(255);
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        writer.append(PrefixCompressedTest.utf("x"));
        writer.append(PrefixCompressedTest.utf(word));
        Assertions.assertEquals(
            List.of("x", word),
            PrefixCompressedTest.read(writer.bytes()),
            "Longest word read back"
        );
    }

    @Test
    void rejectsTooLongWord() {
        final PrefixCompressedWriter writer = new PrefixCompressedWriter();
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> writer.append(new byte[256]),
            "Word over 255 bytes"
        );
    }

    @Test
    void keepsReaderInputIsolated() {
        final byte[] data = {PrefixCompressedTest.len(1), 'a'};
        final PrefixCompressedReader reader = new PrefixCompressedReader(data);
        data[1] = 'z';
        Assertions.assertArrayEquals(
            PrefixCompressedTest.utf("a"), reader.next(), "Input copied"
        );
    }

    @Test
    void returnsIndependentWords() {
        final PrefixCompressedReader reader = new PrefixCompressedReader(
            new byte[] {
                PrefixCompressedTest.len(2), 'a', 'b',
                PrefixCompressedTest.len(2), PrefixCompressedTest.len(1), 'c',
            }
        );
        reader.next()[0] = 'z';
        Assertions.assertArrayEquals(
            PrefixCompressedTest.utf("abc"), reader.next(), "Word copied"
        );
    }

    @Test
    void endsAfterLastWord() {
        final PrefixCompressedReader reader = new PrefixCompressedReader(
            new byte[] {PrefixCompressedTest.len(1), 'a'}
        );
        reader.next();
        Assertions.assertFalse(reader.hasNext(), "At end");
    }

    @Test
    void rejectsReadPastEnd() {
        Assertions.assertThrows(
            NoSuchElementException.class,
            new PrefixCompressedReader(new byte[0])::next,
            "Read past end"
        );
    }

    @Test
    void rejectsMissingLength() {
        final PrefixCompressedReader reader = new PrefixCompressedReader(
            new byte[] {
                PrefixCompressedTest.len(1), 'a', PrefixCompressedTest.len(1),
            }
        );
        reader.next();
        Assertions.assertThrows(
            DatabaseCorruptError.class, reader::next, "Nothing after keep"
        );
    }

    @Test
    void rejectsKeepBeyondWord() {
        final PrefixCompressedReader reader = new PrefixCompressedReader(
            new byte[] {
                PrefixCompressedTest.len(1), 'a',
                PrefixCompressedTest.len(2), PrefixCompressedTest.len(1), 'b',
            }
        );
        reader.next();
        Assertions.assertThrows(
            DatabaseCorruptError.class, reader::next, "Keep too long"
        );
    }

    @Test
    void acceptsKeepOfWholeWord() {
        final PrefixCompressedReader reader = new PrefixCompressedReader(
            new byte[] {
                PrefixCompressedTest.len(1), 'a',
                PrefixCompressedTest.len(1), PrefixCompressedTest.len(1), 'b',
            }
        );
        reader.next();
        Assertions.assertArrayEquals(
            PrefixCompressedTest.utf("ab"), reader.next(), "Keep all"
        );
    }

    @Test
    void rejectsTruncatedWord() {
        Assertions.assertThrows(
            DatabaseCorruptError.class,
            new PrefixCompressedReader(
                new byte[] {PrefixCompressedTest.len(2), 'a'}
            )::next,
            "Word cut short"
        );
    }

    @Test
    void acceptsWordEndingInput() {
        Assertions.assertArrayEquals(
            PrefixCompressedTest.utf("ab"),
            new PrefixCompressedReader(
                new byte[] {PrefixCompressedTest.len(2), 'a', 'b'}
            ).next(),
            "Word up to the end"
        );
    }

    private static byte len(final int length) {
        return (byte) (length + 96);
    }

    private static byte[] utf(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> read(final byte[] data) {
        final PrefixCompressedReader reader = new PrefixCompressedReader(data);
        final List<String> result = new ArrayList<>(0);
        while (reader.hasNext()) {
            result.add(new String(reader.next(), StandardCharsets.UTF_8));
        }
        return result;
    }
}
