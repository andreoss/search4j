/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.Enquire;
import io.github.andreoss.search4j.core.api.EnquireImpl;
import io.github.andreoss.search4j.core.api.PositionIterator;
import io.github.andreoss.search4j.core.api.PostingIterator;
import io.github.andreoss.search4j.core.api.TermIterator;
import io.github.andreoss.search4j.core.api.ValueIterator;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.error.DocNotFoundError;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.DocLength;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Public database handle over one shard.
 *
 * <p>Arguments are validated here, before the shard sees them: identifier
 * zero, an empty term where one is needed and an empty metadata key raise an
 * invalid-argument error. Backend lists are exposed as public iterators.</p>
 *
 * @since 0.1.0
 */
public final class ShardDatabase implements WritableDatabase {

    /**
     * The shard read and written.
     */
    private final Shard shard;

    /**
     * Creates a handle.
     *
     * @param shard Shard to read and write
     */
    public ShardDatabase(final Shard shard) {
        this.shard = shard;
    }

    @Override
    public void addDatabase(final Database database) {
        throw new UnimplementedError("Combining databases is not supported");
    }

    @Override
    public TermCount docCount() {
        return this.shard.docCount();
    }

    @Override
    public DocId lastDocId() {
        return this.shard.lastDocId();
    }

    @Override
    public DocLength averageLength() {
        final int count = this.docCount().value();
        DocLength result = DocLength.ZERO;
        if (count > 0) {
            result = new DocLength(
                (double) this.shard.totalLength().value() / count
            );
        }
        return result;
    }

    @Override
    public TotalLength totalLength() {
        return this.shard.totalLength();
    }

    @Override
    public TermCount termFrequency(final String term) {
        final TermCount result;
        if (term.isEmpty()) {
            result = this.docCount();
        } else {
            result = this.shard.frequencies(term).termFrequency();
        }
        return result;
    }

    @Override
    public TermCount collectionFrequency(final String term) {
        final TermCount result;
        if (term.isEmpty()) {
            result = this.docCount();
        } else {
            result = this.shard.frequencies(term).collection();
        }
        return result;
    }

    @Override
    public boolean termExists(final String term) {
        final boolean result;
        if (term.isEmpty()) {
            result = this.docCount().value() > 0;
        } else {
            result = this.shard.termExists(term);
        }
        return result;
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return this.shard.docLength(ShardDatabase.valid(docid));
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        return this.shard.uniqueTerms(ShardDatabase.valid(docid));
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        return this.shard.wdfDocMax(ShardDatabase.valid(docid));
    }

    @Override
    public TermCount docLengthLowerBound() {
        return this.shard.docLengthLowerBound();
    }

    @Override
    public TermCount docLengthUpperBound() {
        return this.shard.docLengthUpperBound();
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        TermCount result = TermCount.ZERO;
        if (!term.isEmpty()) {
            result = this.shard.wdfUpperBound(term);
        }
        return result;
    }

    @Override
    public TermCount valueFrequency(final ValueNo slot) {
        return this.shard.valueStats(slot).frequency();
    }

    @Override
    public String valueLowerBound(final ValueNo slot) {
        return this.shard.valueStats(slot).lower();
    }

    @Override
    public String valueUpperBound(final ValueNo slot) {
        return this.shard.valueStats(slot).upper();
    }

    @Override
    public boolean hasPositions() {
        return this.shard.hasPositions();
    }

    @Override
    public PostingIterator postings(final String term) {
        return new PostingAdapter(this.shard.openPostList(term), this.shard);
    }

    @Override
    public TermIterator termList(final DocId docid) {
        return new TermAdapter(this.shard.openTermList(ShardDatabase.valid(docid)));
    }

    @Override
    public PositionIterator positions(final DocId docid, final String term) {
        return new PositionAdapter(
            this.shard.openPositionList(
                ShardDatabase.valid(docid), ShardDatabase.termed(term)
            )
        );
    }

    @Override
    public TermCount positionCount(final DocId docid, final String term) {
        return this.shard.openPositionList(
            ShardDatabase.valid(docid), ShardDatabase.termed(term)
        ).approxSize();
    }

    @Override
    public TermIterator allTerms(final String prefix) {
        return new TermAdapter(this.shard.openAllTerms(prefix));
    }

    @Override
    public ValueIterator valueStream(final ValueNo slot) {
        return new ValueAdapter(this.shard.openValueList(slot));
    }

    @Override
    public String metadata(final String key) {
        return this.shard.metadata(ShardDatabase.keyed(key));
    }

    @Override
    public TermIterator metadataKeys(final String prefix) {
        return new TermAdapter(this.shard.openMetadataKeys(prefix));
    }

    @Override
    public Document document(final DocId docid) {
        return this.shard.openDocument(ShardDatabase.valid(docid), false)
            .orElseThrow(() -> ShardDatabase.missing(docid));
    }

    @Override
    public DocId addDocument(final Document document) {
        return this.shard.addDocument(document);
    }

    @Override
    public void deleteDocument(final DocId docid) {
        this.shard.deleteDocument(docid);
    }

    @Override
    public void deleteDocument(final String term) {
        this.shard.deleteDocument(ShardDatabase.termed(term));
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        this.shard.replaceDocument(ShardDatabase.valid(docid), document);
    }

    @Override
    public DocId replaceDocument(final String term, final Document document) {
        return this.shard.replaceDocument(ShardDatabase.termed(term), document);
    }

    @Override
    public void setMetadata(final String key, final String value) {
        this.shard.setMetadata(ShardDatabase.keyed(key), value);
    }

    @Override
    public void beginTransaction(final boolean flushed) {
        this.shard.beginTransaction(flushed);
    }

    @Override
    public void commitTransaction() {
        this.shard.endTransaction(true);
    }

    @Override
    public void cancelTransaction() {
        this.shard.endTransaction(false);
    }

    @Override
    public void commit() {
        this.shard.commit();
    }

    @Override
    public void cancel() {
        this.shard.cancel();
    }

    @Override
    public void close() {
        this.shard.close();
    }

    @Override
    public Enquire enquire() {
        return new EnquireImpl(this.shard);
    }

    private static DocId valid(final DocId docid) {
        if (!docid.valid()) {
            throw new InvalidArgumentError("Document identifier 0 is invalid");
        }
        return docid;
    }

    private static String termed(final String term) {
        if (term.isEmpty()) {
            throw new InvalidArgumentError("Empty terms are invalid");
        }
        return term;
    }

    private static String keyed(final String key) {
        if (key.isEmpty()) {
            throw new InvalidArgumentError("Empty metadata keys are invalid");
        }
        return key;
    }

    private static DocNotFoundError missing(final DocId docid) {
        return new DocNotFoundError(
            String.format("Document %s not found", docid)
        );
    }
}
