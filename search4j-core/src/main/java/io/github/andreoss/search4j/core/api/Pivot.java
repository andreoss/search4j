/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * Pivoted length normalisation of the tf-idf scheme.
 *
 * @param average Average document length
 * @param slope Slope of the normalisation, above zero
 * @param delta Addend of the normalised frequency, above zero
 * @since 0.1.0
 */
record Pivot(double average, double slope, double delta) {
}
