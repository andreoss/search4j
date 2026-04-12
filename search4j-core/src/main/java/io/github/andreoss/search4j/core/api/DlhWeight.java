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

/**
 * DLH weighting from the divergence from randomness framework, free of parameters.
 *
 * @since 0.1.0
 */
public final class DlhWeight implements Weight {

    /**
     * Name of the scheme.
     */
    private final String label;

    /**
     * Creates the scheme.
     */
    public DlhWeight() {
        this.label = "dlh";
    }

    @Override
    public String name() {
        return this.label;
    }

    @Override
    public byte[] serialise() {
        return new byte[0];
    }

    @Override
    public Weight unserialise(final byte[] data) {
        WeightBytes.of(this.name(), data).end();
        return new DlhWeight();
    }

    @Override
    public Weight create(final String params) {
        if (!params.isEmpty()) {
            throw new InvalidArgumentError(
                "No parameters are required for DLHWeight"
            );
        }
        return new DlhWeight();
    }

    @Override
    public TermWeight term(final WeightStats stats, final double factor) {
        return DlhPart.prepare(stats, factor);
    }
}
