/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Term iterator stub.
 *
 * @since 0.1.0
 */
final class TermStub implements TermIterator {

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public String next() {
        return "term";
    }

    @Override
    public TermCount frequency() {
        return new TermCount(2);
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
