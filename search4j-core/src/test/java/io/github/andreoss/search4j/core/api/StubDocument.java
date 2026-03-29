/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Optional;

/**
 * In-memory document used to exercise the {@link Document} contract.
 *
 * @since 0.1.0
 */
final class StubDocument implements Document {

    /**
     * The stored value, if any.
     */
    private String stored;

    /**
     * Whether a value is present.
     */
    private boolean present;

    /**
     * The distinct term count.
     */
    private TermCount terms = TermCount.ZERO;

    @Override
    public String data() {
        return "";
    }

    @Override
    public void setData(final String data) {
        // No backing store in the stub.
    }

    @Override
    public Optional<String> value(final ValueNo slot) {
        Optional<String> result = Optional.empty();
        if (this.present) {
            result = Optional.of(this.stored);
        }
        return result;
    }

    @Override
    public void addValue(final ValueNo slot, final String value) {
        this.stored = value;
        this.present = true;
    }

    @Override
    public void removeValue(final ValueNo slot) {
        this.present = false;
        this.stored = "";
    }

    @Override
    public void addTerm(final String term, final TermCount increment) {
        this.terms = this.terms.plus(increment);
    }

    @Override
    public void removeTerm(final String term, final TermCount decrement) {
        this.terms = new TermCount(this.terms.value() - decrement.value());
    }

    @Override
    public void clearTerms() {
        this.terms = TermCount.ZERO;
    }

    @Override
    public TermCount termCount() {
        return this.terms;
    }

    @Override
    public TermCount valueCount() {
        TermCount result = TermCount.ZERO;
        if (this.present) {
            result = new TermCount(1);
        }
        return result;
    }
}
