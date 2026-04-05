/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.HashSet;
import java.util.Set;

/**
 * Accepts documents whose value in a slot belongs to a set.
 *
 * <p>A document without a value in the slot is rejected. The comparison is
 * exact, on the stored value string.</p>
 *
 * @since 0.1.0
 */
public final class ValueSetMatchDecider implements MatchDecider {

    /**
     * Database whose values are read.
     */
    private final Shard shard;

    /**
     * Slot read.
     */
    private final ValueNo slot;

    /**
     * Accepted values.
     */
    private final Set<String> values;

    /**
     * Whether a document with an accepted value is included.
     */
    private final boolean include;

    /**
     * Creates the decider.
     *
     * @param shard Database whose values are read
     * @param slot Slot read
     * @param values Accepted values
     */
    public ValueSetMatchDecider(
        final Shard shard, final ValueNo slot, final Set<String> values
    ) {
        this(shard, slot, values, true);
    }

    /**
     * Creates the decider.
     *
     * @param shard Database whose values are read
     * @param slot Slot read
     * @param values Values compared
     * @param include Whether a matching document is accepted; false reverses
     *  the decision
     */
    public ValueSetMatchDecider(
        final Shard shard, final ValueNo slot, final Set<String> values,
        final boolean include
    ) {
        this.shard = shard;
        this.slot = slot;
        this.values = new HashSet<>(values);
        this.include = include;
    }

    @Override
    public boolean decide(final DocId docid) {
        final boolean member = this.member(docid);
        final boolean result;
        if (this.include) {
            result = member;
        } else {
            result = !member;
        }
        return result;
    }

    private boolean member(final DocId docid) {
        return this.shard.openDocument(docid, false)
            .flatMap(document -> document.value(this.slot))
            .map(this.values::contains)
            .orElse(false);
    }
}
