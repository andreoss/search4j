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
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Raw deflate compression and decompression.
 *
 * <p>Uses the JDK zlib implementation with no stream wrapper, matching the
 * raw deflate format used by the on-disk layout.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings({"PMD.ProhibitPublicStaticMethods", "PMD.CloseResource"})
public final class Zlib {

    /**
     * Utility class, no instances.
     */
    private Zlib() {
    }

    /**
     * Compresses bytes with raw deflate.
     *
     * @param input Bytes to compress
     * @return Raw deflate stream, without header or checksum
     */
    public static byte[] compress(final byte[] input) {
        final Deflater deflater = new Deflater(
            Deflater.DEFAULT_COMPRESSION, true
        );
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            deflater.setInput(input);
            deflater.finish();
            final byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Decompresses bytes produced by {@link #compress(byte[])}.
     *
     * @param input Raw deflate stream
     * @return Decompressed bytes
     * @throws IOException When the input is not valid, complete deflate data
     */
    public static byte[] decompress(final byte[] input) throws IOException {
        return Zlib.decompress(input, true);
    }

    /**
     * Decompresses raw or wrapped deflate bytes.
     *
     * @param input Deflate stream
     * @param raw Whether the stream has no header nor checksum
     * @return Decompressed bytes
     * @throws IOException When the input is not valid, ends early or needs
     *  a dictionary
     */
    static byte[] decompress(final byte[] input, final boolean raw)
        throws IOException {
        final Inflater inflater = new Inflater(raw);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            inflater.setInput(input);
            final byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                final int count = inflater.inflate(buffer);
                if (count == 0 && !inflater.finished()) {
                    throw new IOException(
                        "Deflate data ends early or needs a dictionary"
                    );
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (final DataFormatException bad) {
            throw new IOException("Bad deflate data", bad);
        } finally {
            inflater.end();
        }
    }
}
