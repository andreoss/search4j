/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.io.IOException;
import java.io.InputStream;

/**
 * Internal weight operations: deserialisation and scheme registration.
 *
 * <p>This class is not part of the public API; it is used by the
 * deserialisation machinery and the Registry.</p>
 *
 * @since 0.1.0
 */
public final class WeightInternal {

    private WeightInternal() {}

    /**
     * Deserialises a weight from an input stream.
     *
     * @param in Stream positioned at the start of a weight
     * @return The weight
     * @throws SerialisationError when the stream is malformed
     */
    public static Weight deserialise(final InputStream in) {
        try {
            final int type = in.read();
            if (type < 0) {
                throw new SerialisationError("Empty weight stream");
            }
            return switch (type) {
                case 1 -> BoolWeight.deserialise(in);
                case 2 -> CoordWeight.deserialise(in);
                case 3 -> BM25Weight.deserialise(in);
                case 4 -> BM25PlusWeight.deserialise(in);
                case 5 -> PL2Weight.deserialise(in);
                case 6 -> PL2PlusWeight.deserialise(in);
                case 7 -> IneB2Weight.deserialise(in);
                case 8 -> IfB2Weight.deserialise(in);
                case 9 -> InL2Weight.deserialise(in);
                case 10 -> DPHWeight.deserialise(in);
                case 11 -> DLHWeight.deserialise(in);
                case 12 -> BB2Weight.deserialise(in);
                default -> throw new SerialisationError(
                    "Unknown weight type: " + type
                );
            };
        } catch (final IOException e) {
            throw new SerialisationError(e);
        }
    }

    /**
     * Deserialises a weight from a byte array.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static Weight deserialise(final byte[] data) {
        return deserialise(new java.io.ByteArrayInputStream(data));
    }
}
