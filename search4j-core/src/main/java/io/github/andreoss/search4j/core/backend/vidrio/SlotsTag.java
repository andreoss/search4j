/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Encoding of the slots a document uses: each slot as its distance one
 * below the previous one, the first as is.
 *
 * @since 0.1.0
 */
final class SlotsTag {

    /**
     * Utility class, no instances.
     */
    private SlotsTag() {
    }

    /**
     * Encodes the slots used.
     *
     * @param slots Slots in ascending order
     * @return Tag bytes, empty for no slots
     */
    static byte[] encode(final Collection<ValueNo> slots) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        long previous = -1L;
        for (final ValueNo slot : slots) {
            out.writeBytes(Varints.pack(slot.value() - previous - 1L));
            previous = slot.value();
        }
        return out.toByteArray();
    }

    /**
     * Decodes the slots used.
     *
     * @param tag Tag bytes
     * @return Slots in ascending order
     */
    static List<ValueNo> decode(final byte[] tag) {
        final ByteReader reader = ByteReader.over(tag);
        final List<ValueNo> result = new ArrayList<>(0);
        long previous = -1L;
        while (!reader.done()) {
            final long slot = previous + 1L + reader.narrow("Value slot");
            if (slot > ValueNo.BAD) {
                throw new DatabaseCorruptError("Value slot encoding corrupt");
            }
            result.add(new ValueNo(slot));
            previous = slot;
        }
        return result;
    }
}
