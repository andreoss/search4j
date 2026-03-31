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

import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import java.io.ByteArrayOutputStream;

/**
 * Start of every posting list chunk: the last-chunk flag as the digit 0
 * or 1, then the distance from the first document of the chunk to its
 * last as a packed integer.
 *
 * @param last Whether the chunk is the last of its list
 * @param end Last document of the chunk
 * @since 0.1.0
 */
record ChunkHeader(boolean last, DocId end) {

    /**
     * Name of the last document field.
     */
    private static final String END = "Last document";

    /**
     * Reads the header.
     *
     * @param reader Reader at the start of the chunk header
     * @param first First document of the chunk
     * @return Header, the reader left after it
     */
    static ChunkHeader read(final ByteReader reader, final DocId first) {
        return new ChunkHeader(
            reader.flag("Last chunk flag"),
            PostingFields.after(
                first, reader.narrow(ChunkHeader.END), ChunkHeader.END
            )
        );
    }

    /**
     * Encodes the header.
     *
     * @param first First document of the chunk, not after the last
     * @return Header bytes
     */
    byte[] bytes(final DocId first) {
        final long gap = (long) this.end.value() - first.value();
        if (gap < 0L) {
            throw new InvariantError("Posting list chunk ends before it starts");
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte flag = '0';
        if (this.last) {
            flag = '1';
        }
        out.write(flag);
        out.writeBytes(Varints.pack(gap));
        return out.toByteArray();
    }
}
