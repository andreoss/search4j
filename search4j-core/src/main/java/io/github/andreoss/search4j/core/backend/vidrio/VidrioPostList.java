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
import io.github.andreoss.search4j.core.backend.list.VectorPositionList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.List;
import java.util.Optional;

/**
 * Posting list of one term of a committed posting list table.
 *
 * <p>Positions are not read yet: both position lists are empty until the
 * position table is ported.</p>
 *
 * @since 0.1.0
 */
public final class VidrioPostList extends LeafPostList {

    /**
     * Table the list is read from.
     */
    private final VidrioTable table;

    /**
     * Walk over the chunks.
     */
    private final ChunkWalker walker;

    /**
     * Creates the list from its parts.
     *
     * @param term Term listed
     * @param table Table the list is read from
     * @param walker Walk over the chunks
     */
    private VidrioPostList(
        final String term, final VidrioTable table, final ChunkWalker walker
    ) {
        super(term);
        this.table = table;
        this.walker = walker;
    }

    /**
     * Opens the list of a term.
     *
     * @param table Posting list table
     * @param term Term listed, empty for the document lengths
     * @return List before its first document
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioPostList open(final VidrioTable table, final String term) {
        return new VidrioPostList(
            term, table,
            ChunkWalker.open(table.cursor(), PostListKeys.bytes(term))
        );
    }

    /**
     * Moves to a document, backwards if need be.
     *
     * @param target Document wanted
     * @return True when the document is in the list
     */
    public boolean jumpTo(final DocId target) {
        return this.walker.jumpTo(target);
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
        return String.format("%s:%s", this.term(), this.termFrequency());
    }

    @Override
    public TermCount termFrequency() {
        return this.walker.freqs().termFrequency();
    }

    @Override
    public TermCount wdf() {
        return this.walker.wdf();
    }

    @Override
    public TermCount collectionFrequency() {
        return this.walker.freqs().collection();
    }

    @Override
    public TermCount wdfUpperBound() {
        return this.walker.bound();
    }

    @Override
    public Optional<LeafPostList> openNearby(
        final String term, final boolean positions
    ) {
        Optional<LeafPostList> result = Optional.empty();
        if (!term.isEmpty()) {
            result = Optional.of(VidrioPostList.open(this.table, term));
        }
        return result;
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
        this.walker.doc();
        return new VectorPositionList(List.of());
    }
}
