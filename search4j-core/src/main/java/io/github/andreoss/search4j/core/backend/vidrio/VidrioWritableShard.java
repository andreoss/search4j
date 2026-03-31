/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.backend.DocumentInternal;
import io.github.andreoss.search4j.core.backend.Revision;
import io.github.andreoss.search4j.core.backend.Shard;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.TransactionState;
import io.github.andreoss.search4j.core.backend.Transactions;
import io.github.andreoss.search4j.core.backend.Uuid;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DatabaseClosedError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A vidrio database directory opened to write.
 *
 * <p>Document changes are buffered and flushed once their number reaches
 * the threshold of the options, or on commit; a failed change drops every
 * pending change. Closing commits unless a transaction is open.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class VidrioWritableShard implements Shard, AutoCloseable {

    /**
     * The session written.
     */
    private final WriteSession session;

    /**
     * Read side of the session.
     */
    private final WritableReads reads;

    /**
     * Indexer of the session.
     */
    private final Indexer indexer;

    /**
     * The transaction state machine.
     */
    private final Transactions states;

    /**
     * Document changes buffered before a flush.
     */
    private final int threshold;

    /**
     * Document changes since the last flush.
     */
    private int changes;

    /**
     * Whether the shard was closed.
     */
    private boolean closed;

    /**
     * Creates the shard over a session.
     *
     * @param session The session written
     * @param reads Read side of the session
     * @param threshold Document changes buffered before a flush
     */
    private VidrioWritableShard(
        final WriteSession session, final WritableReads reads,
        final int threshold
    ) {
        this.session = session;
        this.reads = reads;
        this.indexer = new Indexer(session, reads);
        this.states = new Transactions(TransactionState.NONE);
        this.threshold = threshold;
    }

    /**
     * Opens a database directory to write as the options say.
     *
     * @param dir Database directory
     * @param options Opening options
     * @return Shard holding the write lock
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioWritableShard open(
        final Path dir, final VidrioOptions options
    ) {
        final WriteSession session = WriteSession.open(dir, options);
        return new VidrioWritableShard(
            session, WritableReads.of(session), options.threshold()
        );
    }

    @Override
    public TermCount docCount() {
        return this.live().docCount();
    }

    @Override
    public DocId lastDocId() {
        return this.live().lastDocId();
    }

    @Override
    public TotalLength totalLength() {
        return this.live().totalLength();
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return this.live().docLength(docid);
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        return this.live().uniqueTerms(docid);
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        return this.live().wdfDocMax(docid);
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        return this.live().frequencies(term);
    }

    @Override
    public boolean termExists(final String term) {
        return this.live().termExists(term);
    }

    @Override
    public boolean hasPositions() {
        return this.live().hasPositions();
    }

    @Override
    public ValueStats valueStats(final ValueNo slot) {
        return this.live().valueStats(slot);
    }

    @Override
    public TermCount docLengthLowerBound() {
        return this.live().docLengthLowerBound();
    }

    @Override
    public TermCount docLengthUpperBound() {
        return this.live().docLengthUpperBound();
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        return this.live().wdfUpperBound(term);
    }

    @Override
    public TermCount uniqueTermsLowerBound() {
        return this.live().uniqueTermsLowerBound();
    }

    @Override
    public PostList openPostList(final String term) {
        return this.live().openPostList(term);
    }

    @Override
    public Optional<LeafPostList> openLeafPostList(
        final String term, final boolean positions
    ) {
        return this.live().openLeafPostList(term, positions);
    }

    @Override
    public TermList openTermList(final DocId docid) {
        return this.live().openTermList(docid);
    }

    @Override
    public TermList openTermListDirect(final DocId docid) {
        return this.live().openTermListDirect(docid);
    }

    @Override
    public AllTermsList openAllTerms(final String prefix) {
        return this.live().openAllTerms(prefix);
    }

    @Override
    public PositionList openPositionList(final DocId docid, final String term) {
        return this.live().openPositionList(docid, term);
    }

    @Override
    public Optional<DocumentInternal> openDocument(
        final DocId docid, final boolean lazy
    ) {
        return this.live().openDocument(docid, lazy);
    }

    @Override
    public AllTermsList openMetadataKeys(final String prefix) {
        return this.live().openMetadataKeys(prefix);
    }

    @Override
    public ValueList openValueList(final ValueNo slot) {
        return this.live().openValueList(slot);
    }

    @Override
    public DocId addDocument(final Document document) {
        this.live();
        final DocId doc = this.indexer.next();
        return this.change(
            () -> {
                this.indexer.add(doc, document);
                return doc;
            }
        );
    }

    @Override
    public void deleteDocument(final DocId docid) {
        this.live().docLength(docid);
        this.change(
            () -> {
                this.indexer.delete(docid);
                return docid;
            }
        );
    }

    @Override
    public void replaceDocument(final DocId docid, final Document document) {
        this.change(
            () -> {
                this.indexer.replace(docid, document);
                return docid;
            }
        );
    }

    @Override
    public String metadata(final String key) {
        return this.live().metadata(key);
    }

    @Override
    public void setMetadata(final String key, final String value) {
        this.live();
        new MetadataWriter(this.session.tables().writer(TableType.POSTLIST))
            .set(key, value);
    }

    @Override
    public void commit() {
        this.live();
        if (this.states.state().active()) {
            throw new InvalidOperationError("Can't commit during a transaction");
        }
        this.flush();
        this.session.apply();
    }

    @Override
    public void cancel() {
        this.live();
        this.session.cancel();
        this.changes = 0;
    }

    @Override
    public Transactions transactions() {
        return this.states;
    }

    @Override
    public void close() {
        if (!this.closed) {
            try {
                if (!this.states.state().active()) {
                    this.commit();
                }
            } finally {
                this.closed = true;
                this.session.close();
            }
        }
    }

    @Override
    public Revision revision() {
        return new Revision(this.session.version().revision().value());
    }

    @Override
    public Optional<Uuid> uuid() {
        return Optional.of(this.session.version().uuid());
    }

    @Override
    public DocRange usedDocIds() {
        return this.live().usedDocIds();
    }

    @Override
    public boolean locked() {
        return !this.closed && WriteLock.held(this.session.dir());
    }

    @Override
    public TermCount size() {
        return new TermCount(1);
    }

    @Override
    public void release(final DocumentInternal document) {
        Objects.requireNonNull(document, "document");
    }

    @Override
    public Shard relock(final boolean writable) {
        this.live();
        final Shard result;
        if (writable) {
            result = this;
        } else {
            this.close();
            result = VidrioShard.open(this.session.dir());
        }
        return result;
    }

    @Override
    public Backend backend() {
        return Backend.VIDRIO;
    }

    @Override
    public String description() {
        return String.format("Vidrio(writable, %s)", this.session.dir());
    }

    private <T> T change(final Supplier<T> work) {
        this.live();
        boolean done = false;
        final T result;
        try {
            result = work.get();
            done = true;
        } finally {
            if (!done) {
                this.cancel();
            }
        }
        this.changes += 1;
        if (this.changes >= this.threshold) {
            this.flush();
            if (!this.states.state().active()) {
                this.session.apply();
            }
        }
        return result;
    }

    private void flush() {
        try {
            this.session.flush();
        } finally {
            this.changes = 0;
        }
    }

    private WritableReads live() {
        if (this.closed) {
            throw new DatabaseClosedError("Database has been closed");
        }
        return this.reads;
    }
}
