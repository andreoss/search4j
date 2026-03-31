/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.types.TermCount;

/**
 * Public term iterator over a backend term list.
 *
 * @since 0.1.0
 */
final class TermAdapter implements TermIterator {

    /**
     * The look-ahead state.
     */
    private final Stage stage;

    /**
     * The list, replaced when it prunes itself.
     */
    private TermList list;

    /**
     * Creates the iterator.
     *
     * @param list Term list, before its first term
     */
    TermAdapter(final TermList list) {
        this.stage = new Stage();
        this.list = list;
    }

    @Override
    public boolean hasNext() {
        return this.stage.ready(this::step, this::end);
    }

    @Override
    public String next() {
        this.stage.take(this::step, this::end);
        return this.list.term();
    }

    @Override
    public void skipTo(final String target) {
        this.stage.skip(
            this::step, this::end,
            () -> {
                this.list = this.list.skipTo(target);
            }
        );
    }

    @Override
    public TermCount wdf() {
        this.stage.shown();
        return this.list.wdf();
    }

    @Override
    public TermCount termFrequency() {
        this.stage.shown();
        return this.list.termFrequency();
    }

    @Override
    public TermCount positionCount() {
        this.stage.shown();
        return this.list.positionCount();
    }

    @Override
    public PositionIterator positions() {
        this.stage.shown();
        return new PositionAdapter(this.list.positionList());
    }

    @Override
    public void close() {
        this.stage.close();
    }

    private void step() {
        this.list = this.list.next();
    }

    private boolean end() {
        return this.list.atEnd();
    }
}
