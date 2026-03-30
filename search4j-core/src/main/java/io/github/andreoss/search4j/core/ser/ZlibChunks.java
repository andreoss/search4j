/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Serialisation, compression and bit-level I/O of the search engine.
 */
package io.github.andreoss.search4j.core.ser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Raw deflate decompression of a stream split into chunks.
 *
 * <p>Each chunk is inflated as it arrives, so the whole compressed input
 * is never held at once.</p>
 *
 * @since 0.1.0
 */
public final class ZlibChunks implements AutoCloseable {

    /**
     * Inflater of the stream.
     */
    private final Inflater inflater;

    /**
     * Output buffer, reused between calls.
     */
    private final byte[] buffer;

    /**
     * Creates a decompressor at the start of a stream.
     */
    public ZlibChunks() {
        this(ZlibChunks.raw());
    }

    /**
     * Creates a decompressor over an inflater.
     *
     * @param inflater Inflater, ended on close
     */
    ZlibChunks(final Inflater inflater) {
        this(inflater, new byte[8192]);
    }

    /**
     * Creates a decompressor over its parts.
     *
     * @param inflater Inflater, ended on close
     * @param buffer Output buffer
     */
    private ZlibChunks(final Inflater inflater, final byte[] buffer) {
        this.inflater = inflater;
        this.buffer = buffer;
    }

    /**
     * Inflates one chunk and appends its output.
     *
     * @param input Chunk bytes, consumed
     * @param out Sink of the inflated bytes
     * @return True once the end of the stream is reached
     * @throws IOException If the data is not valid raw deflate
     */
    public boolean feed(
        final ByteBuffer input, final ByteArrayOutputStream out
    ) throws IOException {
        this.inflater.setInput(input);
        try {
            int count;
            do {
                count = this.inflater.inflate(this.buffer);
                out.write(this.buffer, 0, count);
            } while (count > 0 && !this.inflater.finished());
            if (this.inflater.needsDictionary()) {
                throw new IOException("Deflate data needs a dictionary");
            }
        } catch (final DataFormatException bad) {
            throw new IOException("Bad deflate data", bad);
        }
        return this.inflater.finished();
    }

    @Override
    public void close() {
        this.inflater.end();
    }

    private static Inflater raw() {
        return new Inflater(true);
    }
}
