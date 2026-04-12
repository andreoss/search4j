/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.Arrays;
import java.util.Optional;

/**
 * Inverse document frequency normalisations of the tf-idf scheme.
 *
 * @since 0.1.0
 */
public enum IdfNorm {

    /**
     * One.
     */
    NONE(1, 'n', "NONE") {
        @Override
        double apply(final WeightStats stats) {
            return 1.0;
        }
    },

    /**
     * Logarithm of the collection size over the term frequency.
     */
    TFIDF(2, 't', "TFIDF") {
        @Override
        double apply(final WeightStats stats) {
            return Math.log(IdfNorm.size(stats) / IdfNorm.freq(stats));
        }
    },

    /**
     * Square of the logarithm of the collection size over the term
     * frequency.
     */
    SQUARE(3, 's', "SQUARE") {
        @Override
        double apply(final WeightStats stats) {
            final double idf = Math.log(IdfNorm.size(stats) / IdfNorm.freq(stats));
            return idf * idf;
        }
    },

    /**
     * Reciprocal of the term frequency.
     */
    FREQ(4, 'f', "FREQ") {
        @Override
        double apply(final WeightStats stats) {
            return 1.0 / IdfNorm.freq(stats);
        }
    },

    /**
     * Logarithm of the odds that a document is not indexed by the term.
     */
    PROB(5, 'p', "PROB") {
        @Override
        double apply(final WeightStats stats) {
            final double size = IdfNorm.size(stats);
            final double freq = IdfNorm.freq(stats);
            double result = 0.0;
            if (size != freq) {
                result = Math.log((size - freq) / freq);
            }
            return result;
        }
    },

    /**
     * Logarithm of the collection size plus one over the term frequency.
     */
    PIVOTED(6, 'P', "PIVOTED") {
        @Override
        double apply(final WeightStats stats) {
            return Math.log((IdfNorm.size(stats) + 1.0) / IdfNorm.freq(stats));
        }
    },

    /**
     * Collection frequency over term frequency.
     */
    GLOBAL_FREQ(7, '\0', "GLOBAL_FREQ") {
        @Override
        double apply(final WeightStats stats) {
            return IdfNorm.global(stats);
        }
    },

    /**
     * Logarithm of one plus collection frequency over term frequency.
     */
    LOG_GLOBAL_FREQ(8, '\0', "LOG_GLOBAL_FREQ") {
        @Override
        double apply(final WeightStats stats) {
            return Math.log(IdfNorm.global(stats) + 1.0);
        }
    },

    /**
     * One plus collection frequency over term frequency.
     */
    INCREMENTED(9, '\0', "INCREMENTED_GLOBAL_FREQ") {
        @Override
        double apply(final WeightStats stats) {
            return IdfNorm.global(stats) + 1.0;
        }
    },

    /**
     * Square root of collection frequency over term frequency less 0.9.
     */
    SQRT_GLOBAL_FREQ(10, '\0', "SQRT_GLOBAL_FREQ") {
        @Override
        double apply(final WeightStats stats) {
            return Math.sqrt(IdfNorm.global(stats) - 0.9);
        }
    };

    /**
     * Serialised code.
     */
    private final int code;

    /**
     * Letter of the three-letter form, zero for none.
     */
    private final char letter;

    /**
     * Keyword of the parameter string form.
     */
    private final String word;

    /**
     * Creates a normalisation.
     *
     * @param code Serialised code
     * @param letter Letter of the three-letter form, zero for none
     * @param word Keyword of the parameter string form
     */
    IdfNorm(final int code, final char letter, final String word) {
        this.code = code;
        this.letter = letter;
        this.word = word;
    }

    /**
     * Serialised code.
     *
     * @return Code
     */
    public int code() {
        return this.code;
    }

    /**
     * Normalised inverse document frequency.
     *
     * @param stats Collection and term statistics
     * @return Normalised value
     */
    abstract double apply(WeightStats stats);

    /**
     * Finds the normalisation of a serialised code.
     *
     * @param code Serialised code
     * @return The normalisation
     */
    static IdfNorm byCode(final int code) {
        for (final IdfNorm norm : IdfNorm.values()) {
            if (norm.code == code) {
                return norm;
            }
        }
        throw new SerialisationError(
            String.format("Unknown document frequency normalisation %d", code)
        );
    }

    /**
     * Finds the normalisation of a keyword.
     *
     * @param word Keyword of the parameter string form
     * @return The normalisation, empty for none
     */
    static Optional<IdfNorm> byWord(final String word) {
        return Arrays.stream(IdfNorm.values())
            .filter(norm -> norm.word.equals(word)).findFirst();
    }

    /**
     * Finds the normalisation of a letter.
     *
     * @param letter Letter of the three-letter form
     * @return The normalisation
     */
    static IdfNorm byLetter(final char letter) {
        for (final IdfNorm norm : IdfNorm.values()) {
            if (norm.letter == letter && letter != '\0') {
                return norm;
            }
        }
        throw new InvalidArgumentError("Normalization string is invalid");
    }

    private static double size(final WeightStats stats) {
        return WeightMath.unsigned(stats.collection().size().value());
    }

    private static double freq(final WeightStats stats) {
        return WeightMath.unsigned(stats.term().freq().value());
    }

    private static double global(final WeightStats stats) {
        return WeightMath.unsigned(stats.term().collFreq().value())
            / IdfNorm.freq(stats);
    }
}
