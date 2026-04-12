/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.matcher.ExtraWeight;

/**
 * Term-independent weight of a scheme without one.
 *
 * @since 0.1.0
 */
final class NoExtra implements ExtraWeight {

    @Override
    public double sumExtra(final DocStats doc) {
        return 0.0;
    }

    @Override
    public double maxExtra() {
        return 0.0;
    }
}
