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
import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.ser.SortableUints;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Keys of the value slots.
 *
 * <p>In the posting list table a value chunk is keyed by a two-byte
 * prefix, the packed slot and the sort-preserving number of its first
 * document; the statistics of a slot by another prefix and the slot as
 * trailing little-endian bytes. In the term list table the slots a
 * document uses are keyed by its sort-preserving number and a zero
 * byte, just after its term list.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class ValueKeys {

    /**
     * Prefix of the value chunk keys.
     */
    private static final byte[] CHUNK = {0x00, (byte) 0xD8};

    /**
     * Prefix of the value statistics keys.
     */
    private static final byte[] STATS = {0x00, (byte) 0xD0};

    /**
     * Utility class, no instances.
     */
    private ValueKeys() {
    }

    /**
     * Key of a value chunk.
     *
     * @param slot Slot of the chunk
     * @param start First document of the chunk
     * @return Key bytes
     */
    public static byte[] chunk(final ValueNo slot, final DocId start) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ValueKeys.CHUNK);
        out.writeBytes(Varints.pack(slot.value()));
        out.writeBytes(SortableUints.pack(start.value()));
        return out.toByteArray();
    }

    /**
     * Key of the statistics of a slot.
     *
     * @param slot Slot number
     * @return Key bytes
     */
    public static byte[] stats(final ValueNo slot) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ValueKeys.STATS);
        long rest = slot.value();
        while (rest != 0L) {
            out.write((int) (rest & 0xFFL));
            rest >>>= Byte.SIZE;
        }
        return out.toByteArray();
    }

    /**
     * Key of the slots a document uses.
     *
     * @param doc Document identifier
     * @return Key bytes
     */
    public static byte[] slots(final DocId doc) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SortableUints.pack(doc.value()));
        out.write(0);
        return out.toByteArray();
    }

    /**
     * First document of the value chunk a key names.
     *
     * @param slot Slot wanted
     * @param key Key of a posting list entry
     * @return First document, or empty for a key that is not a value chunk
     *  of the slot; a corrupt-data error for a damaged value chunk key
     */
    public static Optional<DocId> chunkStart(final ValueNo slot, final ItemKey key) {
        final byte[] data = key.data();
        Optional<DocId> result = Optional.empty();
        if (data.length >= ValueKeys.CHUNK.length
            && data[0] == ValueKeys.CHUNK[0]
            && data[1] == ValueKeys.CHUNK[1]) {
            final ByteReader reader =
                ByteReader.over(data, ValueKeys.CHUNK.length, data.length);
            if (reader.narrow("Value chunk key slot") == slot.value()) {
                result = Optional.of(
                    ValueKeys.document(data, reader.offset(), key)
                );
            }
        }
        return result;
    }

    private static DocId document(
        final byte[] data, final int offset, final ItemKey key
    ) {
        final ByteBuffer buf = ByteBuffer.wrap(data, offset, data.length - offset);
        final long number;
        try {
            number = SortableUints.unpack(buf);
        } catch (final SerialisationError ex) {
            final DatabaseCorruptError error = new DatabaseCorruptError(
                String.format("Bad document number in value key %s", key)
            );
            error.initCause(ex);
            throw error;
        }
        if (buf.hasRemaining() || number < 1L || number > Integer.MAX_VALUE) {
            throw new DatabaseCorruptError(
                String.format("Bad value key %s", key)
            );
        }
        return DocId.of((int) number);
    }
}
