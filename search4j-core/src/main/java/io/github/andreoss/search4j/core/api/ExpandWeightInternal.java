/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Internal expand weight operations: deserialisation and scheme registration.
 *
 * <p>This class is not part of the public API; it is used by the
 * deserialisation machinery and the Registry.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class ExpandWeightInternal {

    private ExpandWeightInternal() {
    }

    /**
     * Deserialises an expansion weight from an input stream.
     *
     * @param source Stream positioned at the start of a weight
     * @return The weight
     */
    public static ExpandWeight deserialise(final InputStream source) {
        try {
            final int type = source.read();
            if (type < 0) {
                throw new SerialisationError("Empty weight stream");
            }
            return switch (type) {
                case 1 -> Bo1EWeight.deserialise(source);
                case 2 -> ProbeWeight.deserialise(source);
                default -> throw new SerialisationError(
                    String.format("Unknown expand weight type: %d", type)
                );
            };
        } catch (final IOException ex) {
            throw new SerialisationError(
                String.format("Expand weight stream failed: %s", ex)
            );
        }
    }

    /**
     * Deserialises an expansion weight from a byte array.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static ExpandWeight deserialise(final byte[] data) {
        return deserialise(new ByteArrayInputStream(data));
    }
}
