/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Reader of serialised scheme parameters: little-endian doubles and bytes.
 *
 * @since 0.1.0
 */
final class WeightBytes {

    /**
     * Bytes of an encoded double.
     */
    private static final int WIDTH = Double.BYTES;

    /**
     * Name of the scheme, for messages.
     */
    private final String scheme;

    /**
     * Remaining bytes.
     */
    private final ByteBuffer data;

    /**
     * Creates a reader.
     *
     * @param scheme Name of the scheme
     * @param data Little-endian buffer over the parameter bytes
     */
    private WeightBytes(final String scheme, final ByteBuffer data) {
        this.scheme = scheme;
        this.data = data;
    }

    /**
     * Creates a reader over serialised parameters.
     *
     * @param scheme Name of the scheme
     * @param data Parameter bytes
     * @return The reader
     */
    static WeightBytes of(final String scheme, final byte[] data) {
        return new WeightBytes(
            scheme, ByteBuffer.wrap(data.clone()).order(ByteOrder.LITTLE_ENDIAN)
        );
    }

    /**
     * Encodes doubles.
     *
     * @param values Values in order
     * @return Their bytes
     */
    static byte[] doubles(final double... values) {
        final ByteBuffer out = ByteBuffer
            .allocate(values.length * WeightBytes.WIDTH)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (final double value : values) {
            out.putDouble(value);
        }
        return out.array();
    }

    /**
     * Reads a double.
     *
     * @return The value
     */
    double number() {
        if (this.data.remaining() < WeightBytes.WIDTH) {
            throw new SerialisationError(
                "Bad encoded double: insufficient data"
            );
        }
        return this.data.getDouble();
    }

    /**
     * Reads the only double of serialised parameters.
     *
     * @param scheme Name of the scheme
     * @param data Parameter bytes
     * @return The value
     */
    static double single(final String scheme, final byte[] data) {
        final WeightBytes input = WeightBytes.of(scheme, data);
        final double value = input.number();
        input.end();
        return value;
    }

    /**
     * Number of unread bytes.
     *
     * @return Count
     */
    int left() {
        return this.data.remaining();
    }

    /**
     * Reads a byte.
     *
     * @return Its unsigned value
     */
    int code() {
        return Byte.toUnsignedInt(this.data.get());
    }

    /**
     * Fails unless every byte was read.
     */
    void end() {
        if (this.data.hasRemaining()) {
            throw new SerialisationError(
                String.format("Extra data in %s parameters", this.scheme)
            );
        }
    }
}
