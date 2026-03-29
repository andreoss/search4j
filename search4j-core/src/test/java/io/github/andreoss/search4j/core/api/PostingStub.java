/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Posting iterator stub.
 *
 * @since 0.1.0
 */
final class PostingStub implements PostingIterator {

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public DocId next() {
        return DocId.of(9);
    }

    @Override
    public TermCount frequency() {
        return new TermCount(1);
    }

    @Override
    public TermCount positionCount() {
        return new TermCount(2);
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
