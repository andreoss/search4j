/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of serialisation, compression and bit-level I/O.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests of {@link ZlibChunks}.
 *
 * @since 0.1.0
 */
final class ZlibChunksTest {

    @Test
    void inflatesStreamSplitIntoChunks() throws IOException {
        final byte[] text = "chunked stream ".repeat(4000)
            .getBytes(StandardCharsets.US_ASCII);
        final byte[] packed = Zlib.compress(text);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final List<Object> seen = new ArrayList<>(3);
        try (ZlibChunks chunks = new ZlibChunks()) {
            final int half = packed.length / 2;
            seen.add(chunks.feed(ByteBuffer.wrap(packed, 0, half), out));
            seen.add(
                chunks.feed(
                    ByteBuffer.wrap(packed, half, packed.length - half), out
                )
            );
        }
        seen.add(Arrays.equals(text, out.toByteArray()));
        Assertions.assertEquals(
            List.of(false, true, true), seen, "Ends at last, round trip"
        );
    }

    @Test
    void inflatesOutputLargerThanBuffer() throws IOException {
        final byte[] text = new byte[100_000];
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final boolean done;
        try (ZlibChunks chunks = new ZlibChunks()) {
            done = chunks.feed(ByteBuffer.wrap(Zlib.compress(text)), out);
        }
        Assertions.assertEquals(
            List.of(true, text.length), List.of(done, out.size()),
            "All output in one chunk"
        );
    }

    @Test
    void reportsUnfinishedStream() throws IOException {
        final byte[] packed = Zlib.compress(new byte[] {1, 2, 3});
        try (ZlibChunks chunks = new ZlibChunks()) {
            Assertions.assertEquals(
                List.of(false, false),
                List.of(
                    chunks.feed(
                        ByteBuffer.wrap(packed, 0, 0),
                        new ByteArrayOutputStream()
                    ),
                    chunks.feed(
                        ByteBuffer.wrap(packed, 0, 1),
                        new ByteArrayOutputStream()
                    )
                ),
                "Not finished"
            );
        }
    }

    @Test
    void rejectsBadData() {
        try (ZlibChunks chunks = new ZlibChunks()) {
            Assertions.assertThrows(
                IOException.class,
                () -> chunks.feed(
                    ByteBuffer.wrap(new byte[] {(byte) 0xFF, (byte) 0xFF}),
                    new ByteArrayOutputStream()
                ),
                "Bad block type"
            );
        }
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void stopsOnTruncatedStream() throws IOException {
        final byte[] packed = Zlib.compress(
            "truncated chunk ".repeat(500).getBytes(StandardCharsets.US_ASCII)
        );
        try (ZlibChunks chunks = new ZlibChunks()) {
            Assertions.assertFalse(
                chunks.feed(
                    ByteBuffer.wrap(packed, 0, packed.length / 2),
                    new ByteArrayOutputStream()
                ),
                "Waits for more input"
            );
        }
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void rejectsStreamNeedingDictionary() {
        try (ZlibChunks chunks = new ZlibChunks(new Inflater())) {
            Assertions.assertThrows(
                IOException.class,
                () -> chunks.feed(
                    ByteBuffer.wrap(ZlibTest.withDictionary()),
                    new ByteArrayOutputStream()
                ),
                "Preset dictionary"
            );
        }
    }
}
