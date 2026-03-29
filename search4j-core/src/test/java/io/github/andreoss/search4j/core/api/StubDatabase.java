/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API contracts.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.DocLength;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;

/**
 * In-memory database used to exercise the {@link Database} contract.
 *
 * @since 0.1.0
 */
final class StubDatabase implements WritableDatabase {

    /**
     * The number of documents.
     */
    private TermCount docs = TermCount.ZERO;

    @Override
    public void addDatabase(final Database database) {
        // No-op in the stub.
    }

    @Override
    public TermCount docCount() {
        return this.docs;
    }

    @Override
    public DocId lastDocId() {
        return DocId.of(this.docs.value());
    }

    @Override
    public DocLength averageLength() {
        return new DocLength(1.0);
    }

    @Override
    public TotalLength totalLength() {
        return new TotalLength(this.docs.value());
    }

    @Override
    public TermCount termFrequency(final String term) {
        return TermCount.ZERO;
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return new TermCount(1);
    }

    @Override
    public Document document(final DocId docid) {
        return new StubDocument();
    }

    @Override
    public DocId addDocument(final Document document) {
        this.docs = this.docs.plus(new TermCount(1));
        return DocId.of(this.docs.value());
    }

    @Override
    public void deleteDocument(final DocId docid) {
        this.docs = new TermCount(this.docs.value() - 1);
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        // No-op in the stub.
    }

    @Override
    public void commit() {
        // No-op in the stub.
    }

    @Override
    public void cancel() {
        // No-op in the stub.
    }

    @Override
    public void close() {
        // No-op in the stub.
    }
}
