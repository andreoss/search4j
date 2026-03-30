/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sixteen-byte unique identifier of a shard, written as 36 hexadecimal
 * characters grouped 8-4-4-4-12.
 *
 * @param high First eight bytes, big-endian
 * @param low Last eight bytes, big-endian
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record Uuid(long high, long low) {

    /**
     * Number of bytes in the binary form.
     */
    public static final int BYTES = 16;

    /**
     * The identifier with every byte zero.
     */
    public static final Uuid NIL = new Uuid(0L, 0L);

    /**
     * Canonical text form, in either letter case.
     */
    private static final Pattern TEXT = Pattern.compile(
        "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"
    );

    /**
     * Generates a random identifier of version 4, variant 1.
     *
     * @return New identifier
     */
    public static Uuid generate() {
        final UUID random = UUID.randomUUID();
        return new Uuid(
            random.getMostSignificantBits(), random.getLeastSignificantBits()
        );
    }

    /**
     * Parses the canonical text form.
     *
     * @param text Thirty-six characters, hyphens after the 8th, 12th, 16th
     *  and 20th hexadecimal digit
     * @return Identifier
     */
    public static Uuid parse(final String text) {
        if (!Uuid.TEXT.matcher(text).matches()) {
            throw new InvalidArgumentError(
                String.format("Not a canonical identifier: %s", text)
            );
        }
        final UUID parsed = UUID.fromString(text);
        return new Uuid(
            parsed.getMostSignificantBits(), parsed.getLeastSignificantBits()
        );
    }

    /**
     * Reads the binary form.
     *
     * @param bytes Exactly sixteen bytes
     * @return Identifier
     */
    public static Uuid of(final byte[] bytes) {
        if (bytes.length != Uuid.BYTES) {
            throw new InvalidArgumentError(
                String.format("Identifier needs 16 bytes, got %d", bytes.length)
            );
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new Uuid(buffer.getLong(), buffer.getLong());
    }

    /**
     * Binary form.
     *
     * @return Sixteen bytes
     */
    public byte[] bytes() {
        return ByteBuffer.allocate(Uuid.BYTES)
            .putLong(this.high).putLong(this.low).array();
    }

    /**
     * Tests whether every byte is zero.
     *
     * @return True for {@link #NIL}
     */
    public boolean nil() {
        return this.high == 0L && this.low == 0L;
    }

    @Override
    public String toString() {
        return new UUID(this.high, this.low).toString();
    }
}
