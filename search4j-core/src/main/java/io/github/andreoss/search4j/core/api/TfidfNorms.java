/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;

/**
 * Normalisations of the tf-idf scheme.
 *
 * @param wdf Within-document frequency normalisation
 * @param idf Inverse document frequency normalisation
 * @param wt Weight normalisation
 * @since 0.1.0
 */
public record TfidfNorms(WdfNorm wdf, IdfNorm idf, WtNorm wt) {

    /**
     * Default normalisations: plain frequency, logarithmic idf.
     */
    public static final TfidfNorms DEFAULT = new TfidfNorms(
        WdfNorm.NONE, IdfNorm.TFIDF, WtNorm.NONE
    );

    /**
     * Parses the three-letter form, such as {@code ntn}.
     *
     * @param letters One letter per normalisation
     * @return The normalisations
     */
    static TfidfNorms parse(final String letters) {
        if (letters.length() != 3) {
            throw new InvalidArgumentError("Normalization string is invalid");
        }
        return new TfidfNorms(
            WdfNorm.byLetter(letters.charAt(0)), IdfNorm.byLetter(letters.charAt(1)),
            WtNorm.byLetter(letters.charAt(2))
        );
    }
}
