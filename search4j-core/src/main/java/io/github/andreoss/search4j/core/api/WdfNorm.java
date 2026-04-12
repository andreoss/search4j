/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Arrays;
import java.util.Optional;

/**
 * Within-document frequency normalisations of the tf-idf scheme.
 *
 * @since 0.1.0
 */
public enum WdfNorm {

    /**
     * The frequency itself.
     */
    NONE(1, 'n') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            return WeightMath.unsigned(wdf.value());
        }
    },

    /**
     * One when the term occurs.
     */
    BOOLEAN(2, 'b') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 1.0;
            if (wdf.value() == 0) {
                result = 0.0;
            }
            return result;
        }
    },

    /**
     * Square of the frequency.
     */
    SQUARE(3, 's') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            return WeightMath.unsigned(wdf.value() * wdf.value());
        }
    },

    /**
     * One plus the logarithm of the frequency.
     */
    LOG(4, 'l') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                result = 1.0 + Math.log(wdf.value());
            }
            return result;
        }
    },

    /**
     * Doubly logarithmic frequency over the pivoted length.
     */
    PIVOTED(5, 'P') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                final double normal = doc.length().value() / pivot.average();
                result = (1.0 + Math.log(1.0 + Math.log(wdf.value())))
                    * (1.0 / (1.0 - pivot.slope() + pivot.slope() * normal))
                    + pivot.delta();
            }
            return result;
        }
    },

    /**
     * Logarithmic frequency over the logarithmic average frequency.
     */
    LOG_AVERAGE(6, 'L') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                double average = 1.0;
                if (doc.length().value() != 0 && doc.unique().value() != 0) {
                    average = (double) doc.length().value() / doc.unique().value();
                }
                result = (1.0 + Math.log(wdf.value())) / (1.0 + Math.log(average));
            }
            return result;
        }
    },

    /**
     * Augmented logarithmic frequency.
     */
    AUG_LOG(7, '\0') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                result = 0.2 + 0.8 * Math.log(1.0 + wdf.value());
            }
            return result;
        }
    },

    /**
     * Square root of the frequency less a half, plus one.
     */
    SQRT(8, '\0') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                result = Math.sqrt(wdf.value() - 0.5) + 1.0;
            }
            return result;
        }
    },

    /**
     * Augmented frequency over the average frequency.
     */
    AUG_AVERAGE(9, '\0') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                result = 0.9 + 0.1 * ((double) wdf.value()
                    / ((double) doc.length().value() / doc.unique().value()));
            }
            return result;
        }
    },

    /**
     * Frequency over the highest frequency of the document.
     */
    MAX(10, 'm') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (doc.wdfMax().value() != 0) {
                result = (double) wdf.value() / doc.wdfMax().value();
            }
            return result;
        }
    },

    /**
     * Augmented frequency over the highest frequency of the document.
     */
    AUG(11, 'a') {
        @Override
        double apply(final TermCount wdf, final DocStats doc, final Pivot pivot) {
            double result = 0.0;
            if (wdf.value() != 0) {
                result = 0.5 + 0.5 * ((double) wdf.value() / doc.wdfMax().value());
            }
            return result;
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
     * Creates a normalisation.
     *
     * @param code Serialised code
     * @param letter Letter of the three-letter form, zero for none
     */
    WdfNorm(final int code, final char letter) {
        this.code = code;
        this.letter = letter;
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
     * Normalised frequency.
     *
     * @param wdf Within-document frequency
     * @param doc Document statistics
     * @param pivot Pivoted length normalisation
     * @return Normalised frequency
     */
    abstract double apply(TermCount wdf, DocStats doc, Pivot pivot);

    /**
     * Finds the normalisation of a serialised code.
     *
     * @param code Serialised code
     * @return The normalisation
     */
    static WdfNorm byCode(final int code) {
        for (final WdfNorm norm : WdfNorm.values()) {
            if (norm.code == code) {
                return norm;
            }
        }
        throw new SerialisationError(
            String.format("Unknown frequency normalisation %d", code)
        );
    }

    /**
     * Finds the normalisation of a keyword.
     *
     * @param word Keyword of the parameter string form
     * @return The normalisation, empty for none
     */
    static Optional<WdfNorm> byWord(final String word) {
        return Arrays.stream(WdfNorm.values())
            .filter(norm -> norm.name().equals(word)).findFirst();
    }

    /**
     * Finds the normalisation of a letter.
     *
     * @param letter Letter of the three-letter form
     * @return The normalisation
     */
    static WdfNorm byLetter(final char letter) {
        for (final WdfNorm norm : WdfNorm.values()) {
            if (norm.letter == letter && letter != '\0') {
                return norm;
            }
        }
        throw new InvalidArgumentError("Normalization string is invalid");
    }
}
