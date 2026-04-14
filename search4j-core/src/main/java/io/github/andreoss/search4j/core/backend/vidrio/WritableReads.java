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
import io.github.andreoss.search4j.core.backend.ShardBounds;
import io.github.andreoss.search4j.core.backend.ShardListings;
import io.github.andreoss.search4j.core.backend.ShardStatistics;
import io.github.andreoss.search4j.core.backend.TermFrequencies;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.backend.list.AllTermsList;
import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.LeafPostList;
import io.github.andreoss.search4j.core.backend.list.PositionList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.TermList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * The read side of a database being written: the tables as changed so
 * far, with the inverter's and the value changes pending on top.
 *
 * <p>Opening a posting list or the terms with a prefix merges the pending
 * postings concerned into the table first, as the reference does;
 * positions are read from the pending changes before the table and are
 * written on commit only.</p>
 *
 * @since 0.1.0
 */
public final class WritableReads
    implements ShardStatistics, ShardBounds, ShardListings, DocumentFetch {

    /**
     * The session written.
     */
    private final WriteSession session;

    /**
     * Read side over the tables as changed so far.
     */
    private final VidrioReader reader;

    /**
     * Creates the read side from its parts.
     *
     * @param session Session written
     * @param reader Read side over the tables as changed so far
     */
    private WritableReads(final WriteSession session, final VidrioReader reader) {
        this.session = session;
        this.reader = reader;
    }

    /**
     * Opens the read side of a session; the term frequencies of term lists
     * carry the pending changes.
     *
     * @param session Session written
     * @return Read side over the tables
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static WritableReads of(final WriteSession session) {
        final PostingTable stored = PostingTable.of(
            new LiveLookup(session, TableType.POSTLIST)
        );
        return new WritableReads(
            session,
            VidrioReader.over(
                session.version(), session,
                ReaderTables.of(
                    session,
                    PostingSources.pending(
                        new LiveLookup(session, TableType.POSTLIST),
                        term -> session.inverter().frequencies(
                            term, stored.frequencies(term)
                        )
                    ),
                    session.inverter().positions()
                )
            )
        );
    }

    @Override
    public TermCount docCount() {
        return this.reader.docCount();
    }

    @Override
    public DocId lastDocId() {
        return this.reader.lastDocId();
    }

    @Override
    public TotalLength totalLength() {
        return this.reader.totalLength();
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return this.session.inverter().lengths().get(docid)
            .orElseGet(() -> this.reader.docLength(docid));
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        return this.reader.uniqueTerms(docid);
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        return this.reader.wdfDocMax(docid);
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        return this.session.inverter().frequencies(
            term, this.reader.frequencies(term)
        );
    }

    @Override
    public boolean termExists(final String term) {
        return this.frequencies(term).termFrequency().value() > 0;
    }

    @Override
    public boolean hasPositions() {
        return this.session.inverter().positions().hasPositions(
            this.session.entries(TableType.POSITION)
        );
    }

    @Override
    public ValueStats valueStats(final ValueNo slot) {
        return this.session.values().stats(slot);
    }

    @Override
    public TermCount docLengthLowerBound() {
        return this.reader.docLengthLowerBound();
    }

    @Override
    public TermCount docLengthUpperBound() {
        return this.reader.docLengthUpperBound();
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        return this.reader.wdfUpperBound(term);
    }

    @Override
    public TermCount uniqueTermsLowerBound() {
        return this.reader.uniqueTermsLowerBound();
    }

    @Override
    public PostList openPostList(final String term) {
        return this.openLeafPostList(term, false)
            .map(PostList.class::cast).orElse(EmptyPostList.INSTANCE);
    }

    @Override
    public Optional<LeafPostList> openLeafPostList(
        final String term, final boolean positions
    ) {
        final PostingWriter writer = new PostingWriter(
            this.session.tables().writer(TableType.POSTLIST)
        );
        if (term.isEmpty()) {
            this.session.inverter().lengths().flush(writer);
        } else {
            this.session.inverter().flushTerm(writer, term);
        }
        return this.reader.openLeafPostList(term, positions);
    }

    @Override
    public TermList openTermList(final DocId docid) {
        return this.reader.openTermList(docid);
    }

    @Override
    public TermList openTermListDirect(final DocId docid) {
        return this.reader.openTermListDirect(docid);
    }

    @Override
    public AllTermsList openAllTerms(final String prefix) {
        this.session.inverter().flushPrefix(
            new PostingWriter(this.session.tables().writer(TableType.POSTLIST)),
            prefix
        );
        return this.reader.openAllTerms(prefix);
    }

    @Override
    public PositionList openPositionList(final DocId docid, final String term) {
        return this.reader.openPositionList(docid, term);
    }

    @Override
    public Optional<DocumentInternal> openDocument(
        final DocId docid, final boolean lazy
    ) {
        return VidrioReader.document(docid, this, lazy);
    }

    @Override
    public AllTermsList openMetadataKeys(final String prefix) {
        return this.reader.openMetadataKeys(prefix);
    }

    @Override
    public String data(final DocId doc) {
        return this.reader.data(doc);
    }

    @Override
    public NavigableMap<ValueNo, String> values(final DocId doc) {
        return this.session.values().values(doc);
    }

    @Override
    public Optional<String> value(final DocId doc, final ValueNo slot) {
        return Optional.of(this.session.values().value(doc, slot))
            .filter(found -> !found.isEmpty());
    }

    @Override
    public TermList terms(final DocId doc) {
        return this.reader.terms(doc);
    }

    @Override
    public boolean positional() {
        return this.hasPositions();
    }

    /**
     * Metadata stored under a key.
     *
     * @param key Non-empty key
     * @return Value, empty when unset
     */
    public String metadata(final String key) {
        return this.reader.metadata(key);
    }

    /**
     * Opens the values of a slot across documents, merging the pending
     * value changes first.
     *
     * @param slot Slot number
     * @return Values in ascending document order
     */
    public ValueList openValueList(final ValueNo slot) {
        if (this.session.values().modified()) {
            this.session.values().merge();
        }
        return this.reader.openValueList(slot);
    }

    /**
     * Range of the document identifiers in use.
     *
     * @return Lowest and highest identifier of a present document
     */
    public DocRange usedDocIds() {
        return this.reader.usedDocIds();
    }
}
