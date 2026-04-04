/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.backend.list.DocStats;

/**
 * Simple extra weight implementation for scaled queries.
 *
 * @since 0.1.0
 */
final class SimpleExtraWeight implements ExtraWeight {

    private final double factor;

    SimpleExtraWeight(final double factor) {
        this.factor = factor;
    }

    @Override
    public double sumExtra(final DocStats doc) {
        return this.factor;
    }

    @Override
    public double maxExtra() {
        return this.factor;
    }
}
