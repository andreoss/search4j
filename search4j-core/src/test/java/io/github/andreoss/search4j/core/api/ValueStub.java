/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Value iterator stub.
 *
 * @since 0.1.0
 */
final class ValueStub implements ValueIterator {

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public String next() {
        return "value";
    }

    @Override
    public ValueNo slot() {
        return new ValueNo(3L);
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
