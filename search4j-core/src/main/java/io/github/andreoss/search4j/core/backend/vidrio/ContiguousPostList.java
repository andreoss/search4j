/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Every document of a database whose identifiers run from 1 to the
 * document count without a gap, listed without reading a table; later
 * changes of the database are not seen.
 *
 * @since 0.9.1
 */
final class ContiguousPostList extends LeafPostList {

    /**
     * Number of documents, which is the last identifier.
     */
    private final TermCount count;

    /**
     * Current identifier, 0 before the first.
     */
    private long current;

    /**
     * Whether the list has moved past the last identifier.
     */
    private boolean ended;

    /**
     * Creates the list before its first identifier.
     *
     * @param count Number of documents, above 0
     */
    ContiguousPostList(final TermCount count) {
        super("");
        this.count = count;
    }

    @Override
    public DocId docId() {
        this.require();
        return new DocId((int) this.current);
    }

    @Override
    public boolean atEnd() {
        return this.ended;
    }

    @Override
    public PostList next(final double least) {
        if (this.current == this.count.value()) {
            this.ended = true;
        } else {
            this.current += 1L;
        }
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        final long want = Integer.toUnsignedLong(target.value());
        if (want > this.current) {
            if (want > this.count.value()) {
                this.ended = true;
            } else {
                this.current = want;
            }
        }
        return this;
    }

    @Override
    public String description() {
        return String.format("ContiguousPostList(1..%s)", this.count);
    }

    @Override
    public TermCount termFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdf() {
        this.require();
        return TermCount.ONE;
    }

    @Override
    public TermCount collectionFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdfUpperBound() {
        return TermCount.ONE;
    }

    @Override
    public Optional<LeafPostList> openNearby(
        final String term, final boolean positions
    ) {
        return Optional.empty();
    }

    @Override
    public PositionList readPositionList() {
        return this.openPositionList();
    }

    @Override
    public PositionList openPositionList() {
        throw new InvalidOperationError(
            "Positions are not meaningful when iterating every document"
        );
    }

    private void require() {
        if (this.current == 0L || this.ended) {
            throw new InvalidOperationError("Posting list is not on a document");
        }
    }
}
