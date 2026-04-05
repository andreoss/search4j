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
 * Internal weight operations: deserialisation of the wire format.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.ProhibitPublicStaticMethods",
    "RefactorSwitch"
})
public final class WeightInternal {

    /**
     * Utility class, no instances.
     */
    private WeightInternal() {
    }

    /**
     * Deserialises a weight from an input stream.
     *
     * @param source Stream positioned at the start of a weight
     * @return The weight
     */
    public static Weight deserialise(final InputStream source) {
        final Weight weight;
        try {
            final int type = source.read();
            if (type < 0) {
                throw new SerialisationError("Empty weight stream");
            }
            weight = WeightInternal.scheme(type, source);
        } catch (final IOException ex) {
            throw new SerialisationError(
                String.format("Weight stream failed: %s", ex)
            );
        }
        return weight;
    }

    /**
     * Deserialises a weight from a byte array.
     *
     * @param data Bytes of exactly one weight
     * @return The weight
     */
    static Weight deserialise(final byte[] data) {
        return WeightInternal.deserialise(new ByteArrayInputStream(data));
    }

    private static Weight scheme(final int type, final InputStream source)
        throws IOException {
        final Weight weight;
        if (type <= 5) {
            weight = WeightInternal.basic(type, source);
        } else if (type <= 10) {
            weight = WeightInternal.intermediate(type, source);
        } else {
            weight = WeightInternal.advanced(type, source);
        }
        return weight;
    }

    private static Weight basic(final int type, final InputStream source)
        throws IOException {
        final Weight weight;
        switch (type) {
            case 1 -> weight = BoolWeight.deserialise(source);
            case 2 -> weight = CoordWeight.deserialise(source);
            case 3 -> weight = BM25Weight.deserialise(source);
            case 4 -> weight = BM25PlusWeight.deserialise(source);
            case 5 -> weight = PL2Weight.deserialise(source);
            default -> throw new SerialisationError(
                String.format("Unknown weight type: %d", type)
            );
        }
        return weight;
    }

    private static Weight intermediate(final int type, final InputStream source)
        throws IOException {
        final Weight weight;
        switch (type) {
            case 6 -> weight = PL2PlusWeight.deserialise(source);
            case 7 -> weight = IneB2Weight.deserialise(source);
            case 8 -> weight = IfB2Weight.deserialise(source);
            case 9 -> weight = InL2Weight.deserialise(source);
            case 10 -> weight = DphWeight.deserialise(source);
            default -> throw new SerialisationError(
                String.format("Unknown weight type: %d", type)
            );
        }
        return weight;
    }

    private static Weight advanced(final int type, final InputStream source)
        throws IOException {
        final Weight weight;
        switch (type) {
            case 11 -> weight = DlhWeight.deserialise(source);
            case 12 -> weight = BB2Weight.deserialise(source);
            case 13 -> weight = LmWeight.deserialise(source);
            case 14 -> weight = TfidfWeight.deserialise(source);
            case 15 -> weight = DiceWeight.deserialise(source);
            default -> throw new SerialisationError(
                String.format("Unknown weight type: %d", type)
            );
        }
        return weight;
    }
}
