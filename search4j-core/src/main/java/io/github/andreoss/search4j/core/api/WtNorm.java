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

/**
 * Weight normalisations of the tf-idf scheme.
 *
 * @since 0.1.0
 */
public enum WtNorm {

    /**
     * The weight itself.
     */
    NONE;

    /**
     * Serialised code.
     *
     * @return Code
     */
    public int code() {
        return 1;
    }

    /**
     * Finds the normalisation of a serialised code.
     *
     * @param code Serialised code
     * @return The normalisation
     */
    static WtNorm byCode(final int code) {
        if (code != 1) {
            throw new SerialisationError(
                String.format("Unknown weight normalisation %d", code)
            );
        }
        return WtNorm.NONE;
    }

    /**
     * Finds the normalisation of a letter.
     *
     * @param letter Letter of the three-letter form
     * @return The normalisation
     */
    static WtNorm byLetter(final char letter) {
        if (letter != 'n') {
            throw new InvalidArgumentError("Normalization string is invalid");
        }
        return WtNorm.NONE;
    }
}
