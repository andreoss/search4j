/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Length normalisation parameters of the BM25 schemes.
 *
 * @param avglen Average document length
 * @param sat Term frequency saturation parameter
 * @param bpar Document length normalisation parameter
 * @since 0.1.0
 */
record Bm25Norm(TermCount avglen, double sat, double bpar) {
}
