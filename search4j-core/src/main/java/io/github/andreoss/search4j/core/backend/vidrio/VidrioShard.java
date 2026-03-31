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
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A vidrio database directory opened to read.
 *
 * <p>Reads see the revision open; {@link #reopen()} moves to the latest
 * revision. After {@link #close()} every read raises a database-closed
 * error.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class VidrioShard implements Shard, AutoCloseable {

    /**
     * Table files of the directory.
     */
    private final TableFiles files;

    /**
     * The transaction state machine.
     */
    private final Transactions states;

    /**
     * The revision open.
     */
    private ReadRevision current;

    /**
     * Whether the shard was closed.
     */
    private boolean closed;

    /**
     * Creates the shard from its parts.
     *
     * @param files Table files of the directory
     * @param current Revision open
     */
    private VidrioShard(final TableFiles files, final ReadRevision current) {
        this.files = files;
        this.current = current;
        this.states = new Transactions(TransactionState.READ_ONLY);
    }

    /**
     * Opens a database directory to read at its latest revision.
     *
     * @param dir Database directory
     * @return Shard open at the latest revision
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioShard open(final Path dir) {
        final TableFiles files = new TableFiles(dir);
        return new VidrioShard(files, ReadRevision.open(files));
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
    public String metadata(final String key) {
        return this.live().metadata(key);
    }

    @Override
    public ValueList openValueList(final ValueNo slot) {
        return this.live().openValueList(slot);
    }

    @Override
    public Transactions transactions() {
        return this.states;
    }

    @Override
    public boolean reopen() {
        this.live();
        final VersionFile latest = new VersionFile(this.files.dir());
        latest.read();
        final boolean moved =
            !latest.revision().equals(this.current.version().revision());
        if (moved) {
            final ReadRevision opened = ReadRevision.over(this.files, latest);
            this.current.tables().close();
            this.current = opened;
        }
        return moved;
    }

    @Override
    public void close() {
        if (!this.closed) {
            this.current.tables().close();
            this.closed = true;
        }
    }

    @Override
    public Revision revision() {
        return new Revision(this.current.version().revision().value());
    }

    @Override
    public Optional<Uuid> uuid() {
        return Optional.of(this.current.version().uuid());
    }

    @Override
    public DocRange usedDocIds() {
        return this.live().usedDocIds();
    }

    @Override
    public boolean locked() {
        return WriteLock.held(this.files.dir());
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
            result = VidrioWritableShard.open(
                this.files.dir(), VidrioOptions.of(DatabaseAction.OPEN.code())
            );
        } else {
            result = this;
        }
        return result;
    }

    @Override
    public Backend backend() {
        return Backend.VIDRIO;
    }

    @Override
    public String description() {
        return String.format("Vidrio(%s)", this.files.dir());
    }

    private VidrioReader live() {
        if (this.closed) {
            throw new DatabaseClosedError("Database has been closed");
        }
        return this.current.reader();
    }
}
