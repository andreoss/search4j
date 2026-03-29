/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Position iterator stub.
 *
 * @since 0.1.0
 */
final class PositionStub implements PositionIterator {

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public TermPos next() {
        return new TermPos(4);
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
