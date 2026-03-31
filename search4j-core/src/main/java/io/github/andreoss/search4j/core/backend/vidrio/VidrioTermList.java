/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Term list of one document of a committed term list table.
 *
 * <p>The term frequency is looked up in the posting list table when asked
 * for, and kept until the next move as the reference keeps it: a frequency
 * of 0 is looked up again. Positions are not read yet: the position list
 * is empty until the position table is ported.</p>
 *
 * @since 0.1.0
 */
public final class VidrioTermList implements TermList {

    /**
     * Decoder of the tag.
     */
    private final TermListReader reader;

    /**
     * Posting list table the term frequencies come from.
     */
    private final PostingTable postings;

    /**
     * Term frequency of the current term, 0 until looked up.
     */
    private TermCount freq;

    /**
     * Whether the list has moved onto its first term.
     */
    private boolean started;

    /**
     * Whether the list has moved past its last term.
     */
    private boolean ended;

    /**
     * Creates the list from its parts.
     *
     * @param reader Decoder of the tag
     * @param postings Posting list table the term frequencies come from
     */
    private VidrioTermList(
        final TermListReader reader, final PostingTable postings
    ) {
        this.reader = reader;
        this.postings = postings;
        this.freq = TermCount.ZERO;
        this.started = false;
        this.ended = false;
    }

    /**
     * Length of the document.
     *
     * @return Sum of the frequencies of its terms
     */
    public TermCount docLength() {
        return this.reader.length();
    }

    /**
     * Number of distinct terms, bounded by the length as the reference
     * bounds it.
     *
     * @return Term count, at most the document length
     */
    public TermCount uniqueTerms() {
        return new TermCount(
            Math.min(this.reader.size().value(), this.reader.length().value())
        );
    }

    @Override
    public TermCount approxSize() {
        return this.reader.size();
    }

    @Override
    public String term() {
        this.require();
        return new String(this.reader.term(), StandardCharsets.UTF_8);
    }

    @Override
    public TermCount wdf() {
        this.require();
        return this.reader.wdf();
    }

    @Override
    public TermCount termFrequency() {
        this.require();
        if (this.freq.value() == 0) {
            this.freq = this.postings.stats(this.term()).freqs().termFrequency();
        }
        return this.freq;
    }

    @Override
    public TermList next() {
        this.started = true;
        if (!this.ended) {
            this.ended = !this.reader.advance();
            this.freq = TermCount.ZERO;
        }
        return this;
    }

    @Override
    public TermList skipTo(final String target) {
        if (!this.started) {
            this.next();
        }
        final byte[] want = PostListKeys.bytes(target);
        while (!this.ended
            && Arrays.compareUnsigned(this.reader.term(), want) < 0) {
            this.next();
        }
        return this;
    }

    @Override
    public boolean atEnd() {
        return this.ended;
    }

    @Override
    public TermCount positionCount() {
        this.require();
        return TermCount.ZERO;
    }

    @Override
    public PositionList positionList() {
        this.require();
        return new VectorPositionList(List.of());
    }

    /**
     * Opens the list over a tag.
     *
     * @param tag Tag bytes of the document, not copied
     * @param postings Posting list table the term frequencies come from
     * @return List before its first term
     */
    static VidrioTermList over(final byte[] tag, final PostingTable postings) {
        return new VidrioTermList(TermListReader.parse(tag), postings);
    }

    private void require() {
        if (!this.started || this.ended) {
            throw new InvalidOperationError("Term list is not on a term");
        }
    }
}
