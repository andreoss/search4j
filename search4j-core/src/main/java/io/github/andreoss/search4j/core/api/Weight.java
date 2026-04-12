/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.matcher.ExtraWeight;

/**
 * A weighting scheme: immutable parameters from which term and
 * term-independent weights are prepared for each search.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public interface Weight {

    /**
     * Name the scheme is registered and serialised under.
     *
     * @return Scheme name
     */
    String name();

    /**
     * Serialises the parameters of the scheme.
     *
     * @return Parameter bytes
     */
    byte[] serialise();

    /**
     * Creates a scheme of this kind from serialised parameters.
     *
     * @param data Parameter bytes of exactly one scheme
     * @return The scheme
     */
    Weight unserialise(byte[] data);

    /**
     * Creates a scheme of this kind from a parameter string.
     *
     * @param params Space separated parameters, empty for the defaults
     * @return The scheme
     */
    Weight create(String params);

    /**
     * Prepares the weight of one query term.
     *
     * @param stats Collection and term statistics
     * @param factor Scale factor of the term, above zero
     * @return Term weight
     */
    TermWeight term(WeightStats stats, double factor);

    /**
     * Prepares the term-independent weight of a document.
     *
     * @param stats Collection statistics with {@link TermStats#EXTRA}
     * @return Extra weight, zero for schemes without one
     */
    default ExtraWeight extra(final WeightStats stats) {
        return new NoExtra();
    }

    /**
     * Creates a scheme from its name followed by its parameters.
     *
     * @param spec Scheme name, optionally a space and parameters
     * @param registry Registry of the schemes
     * @return The scheme
     */
    static Weight create(final String spec, final Registry registry) {
        final int space = spec.indexOf(' ');
        final String scheme;
        final String params;
        if (space < 0) {
            scheme = spec;
            params = "";
        } else {
            scheme = spec.substring(0, space);
            params = spec.substring(space + 1);
        }
        final Weight weight;
        if (registry.hasWeightingScheme(scheme)) {
            weight = registry.getWeightingScheme(scheme).create(params);
        } else if ("trad".equals(scheme)) {
            weight = BM25Weight.trad(params);
        } else {
            throw new InvalidArgumentError(
                String.format("Unknown weighting scheme: %s", scheme)
            );
        }
        return weight;
    }
}
