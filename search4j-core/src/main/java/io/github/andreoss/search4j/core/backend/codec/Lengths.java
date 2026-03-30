/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Byte encodings of string lists used by the database backends.
 */
package io.github.andreoss.search4j.core.backend.codec;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;

/**
 * Single-byte lengths, masked so that short lengths are printable.
 *
 * @since 0.1.0
 */
final class Lengths {

    /**
     * The mask applied to every length byte.
     */
    private static final int MASK = 96;

    /**
     * Utility class, no instances.
     */
    private Lengths() {
    }

    /**
     * Encodes a length into its byte.
     *
     * @param length Length from 0 to 255
     * @return Masked byte value
     */
    static int encode(final int length) {
        if (length < 0 || length > 255) {
            throw new InvalidArgumentError(
                String.format("Length %d does not fit in one byte", length)
            );
        }
        return length ^ Lengths.MASK;
    }

    /**
     * Decodes a length from its byte.
     *
     * @param data Masked byte
     * @return Length from 0 to 255
     */
    static int decode(final byte data) {
        return (data & 0xFF) ^ Lengths.MASK;
    }
}
