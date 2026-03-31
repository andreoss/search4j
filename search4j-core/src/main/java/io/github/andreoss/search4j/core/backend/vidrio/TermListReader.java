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
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Arrays;

/**
 * Decoder of a term list tag written as {@link TermListTag} lays it out.
 *
 * <p>The header is read at once; the terms are decoded as the reader
 * advances. Running out of data, a field wider than its type and a term
 * count other than the number of terms held raise
 * {@link DatabaseCorruptError}.</p>
 *
 * @since 0.1.0
 */
final class TermListReader {

    /**
     * Reader over the tag.
     */
    private final ByteReader reader;

    /**
     * Document length.
     */
    private final TermCount length;

    /**
     * Number of terms the header claims.
     */
    private final TermCount size;

    /**
     * Term decoded last.
     */
    private byte[] current;

    /**
     * Frequency of the term decoded last.
     */
    private TermCount wdf;

    /**
     * Number of terms decoded.
     */
    private int seen;

    /**
     * Creates a reader from its parts.
     *
     * @param reader Reader over the tag, after the header
     * @param length Document length
     * @param size Number of terms the header claims
     */
    private TermListReader(
        final ByteReader reader, final TermCount length, final TermCount size
    ) {
        this.reader = reader;
        this.length = length;
        this.size = size;
        this.current = new byte[0];
        this.wdf = TermCount.ZERO;
        this.seen = 0;
    }

    /**
     * Reads the header of a tag.
     *
     * @param tag Tag bytes, not copied
     * @return Reader before the first term
     */
    static TermListReader parse(final byte[] tag) {
        final ByteReader reader = ByteReader.over(tag);
        TermCount length = TermCount.ZERO;
        TermCount size = TermCount.ZERO;
        if (!reader.done()) {
            length = PostingFields.count(reader, "Document length");
            size = PostingFields.count(reader, "Term count");
        }
        return new TermListReader(reader, length, size);
    }

    /**
     * Document length.
     *
     * @return Sum of the frequencies of the terms
     */
    TermCount length() {
        return this.length;
    }

    /**
     * Number of terms.
     *
     * @return Count from the header
     */
    TermCount size() {
        return this.size;
    }

    /**
     * Decodes the next term.
     *
     * @return False once every term was decoded
     */
    boolean advance() {
        final boolean more = !this.reader.done();
        if (more) {
            this.seen += 1;
            this.decode();
        } else if (this.seen != this.size.value()) {
            throw new DatabaseCorruptError(
                String.format(
                    "Term count %s does not match the %d terms held",
                    this.size, this.seen
                )
            );
        }
        return more;
    }

    /**
     * Term decoded last.
     *
     * @return Copy of the term bytes
     */
    byte[] term() {
        return this.current.clone();
    }

    /**
     * Frequency of the term decoded last.
     *
     * @return Within-document frequency
     */
    TermCount wdf() {
        return this.wdf;
    }

    private void decode() {
        int keep = 0;
        boolean packed = false;
        if (this.current.length > 0) {
            keep = this.reader.octet("Term prefix length");
            if (keep > this.current.length) {
                final int divisor = this.current.length + 1;
                this.wdf = new TermCount(keep / divisor - 1);
                keep %= divisor;
                packed = true;
            }
        }
        final int add = this.reader.octet("Term length");
        final byte[] tail = this.reader.bytes(add, "Term bytes");
        final byte[] term = Arrays.copyOf(this.current, keep + add);
        System.arraycopy(tail, 0, term, keep, add);
        this.current = term;
        if (!packed) {
            this.wdf = PostingFields.count(
                this.reader, "Within-document frequency"
            );
        }
    }
}
