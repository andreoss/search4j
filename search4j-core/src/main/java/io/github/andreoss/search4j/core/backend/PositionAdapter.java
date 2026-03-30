/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.types.TermPos;

/**
 * Public position iterator over a backend position list.
 *
 * @since 0.1.0
 */
final class PositionAdapter implements PositionIterator {

    /**
     * The look-ahead state.
     */
    private final Stage stage;

    /**
     * The list.
     */
    private final PositionList list;

    /**
     * Whether the list is exhausted.
     */
    private boolean over;

    /**
     * Creates the iterator.
     *
     * @param list The position list, before its first position
     */
    PositionAdapter(final PositionList list) {
        this.stage = new Stage();
        this.list = list;
        this.over = false;
    }

    @Override
    public boolean hasNext() {
        return this.stage.ready(this::step, this::end);
    }

    @Override
    public TermPos next() {
        this.stage.take(this::step, this::end);
        return this.list.position();
    }

    @Override
    public void skipTo(final TermPos target) {
        this.stage.skip(
            this::step, this::end,
            () -> {
                this.over = !this.list.skipTo(target);
            }
        );
    }

    @Override
    public void close() {
        this.stage.close();
    }

    private void step() {
        this.over = !this.list.next();
    }

    private boolean end() {
        return this.over;
    }
}
