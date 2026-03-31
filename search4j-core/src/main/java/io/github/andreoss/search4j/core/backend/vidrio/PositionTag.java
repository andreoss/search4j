/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.ser.BitReader;
import io.github.andreoss.search4j.core.ser.BitWriter;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TermPos;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Encoding of a position list as the reference stores it: the last
 * position as a packed integer, then, for more than one position, a bit
 * stream of the first position below the last, the count less two below
 * their distance, and the positions between them coded interpolatively.
 *
 * @since 0.1.0
 */
final class PositionTag {

    /**
     * Message of data that cannot be decoded.
     */
    private static final String CORRUPT = "Position list data corrupt";

    /**
     * Utility class, no instances.
     */
    private PositionTag() {
    }

    /**
     * Encodes positions.
     *
     * @param positions Strictly ascending positions, at least one
     * @return Tag bytes
     */
    static byte[] encode(final List<TermPos> positions) {
        if (positions.isEmpty()) {
            throw new InvalidArgumentError("Position list is empty");
        }
        final long[] values = new long[positions.size()];
        for (int idx = 0; idx < values.length; ++idx) {
            values[idx] = positions.get(idx).value();
            if (idx > 0 && values[idx] <= values[idx - 1]) {
                throw new InvalidArgumentError(
                    String.format(
                        "Positions are not ascending at %s", positions.get(idx)
                    )
                );
            }
        }
        final int last = values.length - 1;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(Varints.pack(values[last]));
        if (last > 0) {
            final BitWriter writer = new BitWriter();
            writer.encode(values[0], values[last]);
            writer.encode(last - 1L, values[last] - values[0]);
            writer.encodeInterpolative(values, 0, last);
            out.writeBytes(writer.freeze());
        }
        return out.toByteArray();
    }

    /**
     * Decodes a tag into a list before its first position.
     *
     * @param data Tag bytes, empty for no positions
     * @return List before its first position
     */
    static VidrioPositionList decode(final byte[] data) {
        final VidrioPositionList result;
        if (data.length == 0) {
            result = new VidrioPositionList(
                new BitReader(data), TermPos.FIRST, TermPos.FIRST,
                TermCount.ZERO
            );
        } else {
            final ByteReader reader = ByteReader.over(data);
            final TermPos last = PositionTag.position(reader, "Last position");
            if (reader.done()) {
                result = new VidrioPositionList(
                    new BitReader(new byte[0]), last, last, new TermCount(1)
                );
            } else {
                result = PositionTag.decodeMany(reader.rest(), last);
            }
        }
        return result;
    }

    private static VidrioPositionList decodeMany(
        final byte[] bits, final TermPos last
    ) {
        if (last.value() == 0) {
            throw new DatabaseCorruptError(PositionTag.CORRUPT);
        }
        try {
            final BitReader reader = new BitReader(bits);
            final long first = reader.decode(last.value());
            final long size = reader.decode(last.value() - first) + 2L;
            if (size > Integer.MAX_VALUE) {
                throw new DatabaseCorruptError(
                    String.format("Position count too large: %d", size)
                );
            }
            reader.decodeInterpolative((int) size - 1, first, last.value());
            return new VidrioPositionList(
                reader, new TermPos((int) first), last,
                new TermCount((int) size)
            );
        } catch (final SerialisationError ex) {
            final DatabaseCorruptError error =
                new DatabaseCorruptError(PositionTag.CORRUPT);
            error.initCause(ex);
            throw error;
        }
    }

    private static TermPos position(final ByteReader reader, final String what) {
        final long value = reader.narrow(what);
        if (value > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("%s too large: %d", what, value)
            );
        }
        return new TermPos((int) value);
    }
}
