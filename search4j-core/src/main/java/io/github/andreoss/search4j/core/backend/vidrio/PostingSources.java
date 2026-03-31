/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import java.util.function.Supplier;

/**
 * Where a read side takes its posting list lookups and term frequencies
 * from: one set of lookups kept over committed tables, a fresh one per
 * call over tables being written.
 *
 * @param postings Lookups in the posting list table
 * @param freqs Source of the term frequencies of term lists
 * @since 0.1.0
 */
public record PostingSources(
    Supplier<PostingTable> postings, TermFrequencySource freqs
) {

    /**
     * Sources over committed tables: the lookups opened once, the term
     * frequencies read from them.
     *
     * @param table Posting list table
     * @return Sources
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static PostingSources committed(final TableLookup table) {
        final PostingTable fixed = PostingTable.of(table);
        return new PostingSources(() -> fixed, fixed);
    }

    /**
     * Sources over tables being written: fresh lookups per call, the term
     * frequencies from another source.
     *
     * @param table Posting list table
     * @param freqs Source of the term frequencies of term lists
     * @return Sources
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static PostingSources pending(
        final TableLookup table, final TermFrequencySource freqs
    ) {
        return new PostingSources(() -> PostingTable.of(table), freqs);
    }
}
