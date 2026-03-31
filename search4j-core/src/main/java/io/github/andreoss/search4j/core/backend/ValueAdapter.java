/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Public value iterator over a backend value list.
 *
 * @since 0.1.0
 */
final class ValueAdapter implements ValueIterator {

    /**
     * The look-ahead state.
     */
    private final Stage stage;

    /**
     * The list.
     */
    private final ValueList list;

    /**
     * Creates the iterator.
     *
     * @param list Value list, before its first value
     */
    ValueAdapter(final ValueList list) {
        this.stage = new Stage();
        this.list = list;
    }

    @Override
    public boolean hasNext() {
        return this.stage.ready(this.list::next, this.list::atEnd);
    }

    @Override
    public String next() {
        this.stage.take(this.list::next, this.list::atEnd);
        return this.list.value();
    }

    @Override
    public ValueNo slot() {
        this.stage.shown();
        return this.list.slot();
    }

    @Override
    public DocId docId() {
        this.stage.shown();
        return this.list.docId();
    }

    @Override
    public void skipTo(final DocId target) {
        this.stage.skip(
            this.list::next, this.list::atEnd, () -> this.list.skipTo(target)
        );
    }

    @Override
    public void close() {
        this.stage.close();
    }
}
