/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table and the version file.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Posting list of every document of a committed posting list table, read
 * from the document length chunks, each document with a frequency of one.
 *
 * @since 0.1.0
 */
public final class VidrioAllDocsPostList extends LeafPostList {

    /**
     * Walk over the document length chunks.
     */
    private final ChunkWalker walker;

    /**
     * Number of documents.
     */
    private final TermCount count;

    /**
     * Creates the list from its parts.
     *
     * @param walker Walk over the document length chunks
     * @param count Number of documents
     */
    private VidrioAllDocsPostList(
        final ChunkWalker walker, final TermCount count
    ) {
        super("");
        this.walker = walker;
        this.count = count;
    }

    /**
     * Opens the list of every document.
     *
     * @param table Posting list table
     * @param count Number of documents in the database
     * @return List before its first document
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioAllDocsPostList open(
        final TableLookup table, final TermCount count
    ) {
        return new VidrioAllDocsPostList(
            ChunkWalker.open(table.cursor(), new byte[0]), count
        );
    }

    @Override
    public DocId docId() {
        return this.walker.doc();
    }

    @Override
    public boolean atEnd() {
        return this.walker.ended();
    }

    @Override
    public PostList next(final double least) {
        this.walker.next();
        return this;
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        this.walker.skipTo(target);
        return this;
    }

    @Override
    public String description() {
        return String.format("VidrioAllDocsPostList(doccount=%s)", this.count);
    }

    @Override
    public TermCount termFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdf() {
        this.walker.doc();
        return new TermCount(1);
    }

    @Override
    public TermCount collectionFrequency() {
        return this.count;
    }

    @Override
    public TermCount wdfUpperBound() {
        return new TermCount(1);
    }

    @Override
    public Optional<LeafPostList> openNearby(
        final String term, final boolean positions
    ) {
        return Optional.empty();
    }

    @Override
    public DocRange docIdRange(final DocRange whole) {
        return this.walker.range(whole);
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
}
