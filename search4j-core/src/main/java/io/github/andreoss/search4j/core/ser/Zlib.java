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
     * @param input The bytes to compress
     * @return The compressed bytes
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
     * @param input The compressed bytes
     * @return The original bytes
     * @throws IOException When the input is not valid deflate data
     */
    public static byte[] decompress(final byte[] input) throws IOException {
        final Inflater inflater = new Inflater(true);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            inflater.setInput(input);
            final byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                out.write(buffer, 0, inflater.inflate(buffer));
            }
            return out.toByteArray();
        } catch (final DataFormatException bad) {
            throw new IOException("Bad deflate data", bad);
        } finally {
            inflater.end();
        }
    }
}
