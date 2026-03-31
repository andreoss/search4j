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

import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.ser.Varints;
import io.github.andreoss.search4j.core.types.DocId;
import java.io.ByteArrayOutputStream;

/**
 * Start of the first chunk of a posting list: the term frequency, the
 * collection frequency and the first document, each a packed integer, the
 * document stored one below its number.
 *
 * @param freqs Frequencies of the term
 * @param first First document of the list
 * @since 0.1.0
 */
record FirstHeader(TermFrequencies freqs, DocId first) {

    /**
     * Name of the first document field.
     */
    private static final String FIRST = "First document";

    /**
     * Reads the header.
     *
     * @param reader Reader at the start of the first chunk
     * @return Header, the reader left after it
     */
    static FirstHeader read(final ByteReader reader) {
        return new FirstHeader(
            new TermFrequencies(
                PostingFields.count(reader, "Term frequency"),
                PostingFields.count(reader, "Collection frequency")
            ),
            PostingFields.after(
                DocId.NONE, reader.narrow(FirstHeader.FIRST) + 1L,
                FirstHeader.FIRST
            )
        );
    }

    /**
     * Encodes the header.
     *
     * @return Header bytes
     */
    byte[] bytes() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(Varints.pack(this.freqs.termFrequency().value()));
        out.writeBytes(Varints.pack(this.freqs.collection().value()));
        out.writeBytes(Varints.pack(this.first.value() - 1L));
        return out.toByteArray();
    }
}
