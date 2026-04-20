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
import java.nio.charset.StandardCharsets;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * The read side of a vidrio database over its tables and version file:
 * statistics, bounds, lists and documents as the tables hold them.
 *
 * <p>A term longer than the format stores is unknown without a lookup.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.CouplingBetweenObjects")
public final class VidrioReader
    implements ShardStatistics, ShardBounds, ShardListings, DocumentFetch {

    /**
     * Version file holding the statistics.
     */
    private final VersionFile version;

    /**
     * The tables.
     */
    private final TableSet tables;

    /**
     * The lookups worked through.
     */
    private final ReaderTables parts;

    /**
     * Creates the read side from its parts.
     *
     * @param version Version file holding the statistics
     * @param tables Tables of the revision
     * @param parts Lookups worked through
     */
    private VidrioReader(
        final VersionFile version, final TableSet tables,
        final ReaderTables parts
    ) {
        this.version = version;
        this.tables = tables;
        this.parts = parts;
    }

    /**
     * Opens the read side over tables, the term frequencies of term lists
     * read from the posting list table.
     *
     * @param version Version file holding the statistics
     * @param tables Tables of the revision
     * @return Read side over the tables
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioReader over(
        final VersionFile version, final TableSet tables
    ) {
        return new VidrioReader(
            version, tables,
            ReaderTables.of(
                tables,
                PostingSources.committed(new LiveLookup(tables, TableType.POSTLIST)),
                new PendingPositions()
            )
        );
    }

    /**
     * Opens the read side over tables being written through lookups made
     * for them: the posting list lookups opened afresh on every call so
     * they see the changes, another source of the term frequencies of term
     * lists, and the positions not yet written read before the position
     * table.
     *
     * @param version Version file holding the statistics
     * @param tables Tables of the revision
     * @param parts Lookups over the tables
     * @return Read side over the tables
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioReader over(
        final VersionFile version, final TableSet tables,
        final ReaderTables parts
    ) {
        return new VidrioReader(version, tables, parts);
    }

    @Override
    public TermCount docCount() {
        return new TermCount(this.version.stats().docs());
    }

    @Override
    public DocId lastDocId() {
        return new DocId((int) this.version.stats().last());
    }

    @Override
    public TotalLength totalLength() {
        return new TotalLength(this.version.stats().total());
    }

    @Override
    public TermCount docLength(final DocId docid) {
        return this.parts.postings().get().docLength(docid);
    }

    @Override
    public TermCount uniqueTerms(final DocId docid) {
        return this.parts.termlists().open(docid).uniqueTerms();
    }

    @Override
    public TermCount wdfDocMax(final DocId docid) {
        TermCount result = TermCount.ZERO;
        TermList list = this.parts.termlists().open(docid).next();
        while (!list.atEnd()) {
            if (list.wdf().compareTo(result) > 0) {
                result = list.wdf();
            }
            list = list.next();
        }
        return result;
    }

    @Override
    public TermFrequencies frequencies(final String term) {
        TermFrequencies result = TermFrequencies.NONE;
        if (VidrioReader.storable(term)) {
            result = this.parts.postings().get().frequencies(term);
        }
        return result;
    }

    @Override
    public boolean termExists(final String term) {
        return VidrioReader.storable(term)
            && this.parts.postings().get().hasTerm(term);
    }

    @Override
    public boolean hasPositions() {
        return !this.tables.entries(TableType.POSITION).empty();
    }

    @Override
    public ValueStats valueStats(final ValueNo slot) {
        return this.parts.values().stats(slot);
    }

    @Override
    public TermCount docLengthLowerBound() {
        return new TermCount(this.version.stats().lower());
    }

    @Override
    public TermCount docLengthUpperBound() {
        return new TermCount(this.version.stats().upper());
    }

    @Override
    public TermCount wdfUpperBound(final String term) {
        TermCount result = TermCount.ZERO;
        if (VidrioReader.storable(term)) {
            result = new TermCount(
                Math.min(
                    this.parts.postings().get().stats(term).wdfUpperBound()
                        .value(),
                    this.version.stats().wdf()
                )
            );
        }
        return result;
    }

    @Override
    public TermCount uniqueTermsLowerBound() {
        return new TermCount(this.version.stats().uniqueTermsLowerBound());
    }

    @Override
    public PostList openPostList(final String term) {
        return this.openLeafPostList(term, false)
            .map(PostList.class::cast).orElse(EmptyPostList.INSTANCE);
    }

    @Override
    public Optional<LeafPostList> openLeafPostList(
        final String term, final boolean needed
    ) {
        Optional<LeafPostList> result = Optional.empty();
        if (term.isEmpty()) {
            final DatabaseStats stats = this.version.stats();
            final TermCount count = new TermCount(stats.docs());
            if (stats.last() == stats.docs() && stats.docs() > 0L) {
                result = Optional.of(new ContiguousPostList(count));
            } else if (stats.docs() > 0L) {
                result = Optional.of(
                    VidrioAllDocsPostList.open(
                        this.tables.lookup(TableType.POSTLIST), count
                    )
                );
            }
        } else if (VidrioReader.storable(term)) {
            final VidrioPostList list = VidrioPostList.open(
                this.tables.lookup(TableType.POSTLIST), this.parts.positions(),
                term
            );
            if (list.termFrequency().value() > 0) {
                result = Optional.of(list);
            }
        }
        return result;
    }

    @Override
    public TermList openTermList(final DocId docid) {
        return this.parts.termlists().open(docid);
    }

    @Override
    public TermList openTermListDirect(final DocId docid) {
        return this.parts.termlists().open(docid);
    }

    @Override
    public AllTermsList openAllTerms(final String prefix) {
        return VidrioAllTermsList.open(
            this.tables.lookup(TableType.POSTLIST),
            this.tables.entries(TableType.POSTLIST), prefix
        );
    }

    @Override
    public PositionList openPositionList(final DocId docid, final String term) {
        return this.parts.positions().open(docid, term);
    }

    @Override
    public Optional<DocumentInternal> openDocument(
        final DocId docid, final boolean lazy
    ) {
        return VidrioReader.document(docid, this, lazy);
    }

    @Override
    public AllTermsList openMetadataKeys(final String prefix) {
        return this.parts.metadata().keys(prefix);
    }

    @Override
    public String data(final DocId doc) {
        return new String(
            this.tables.lookup(TableType.DOCDATA).get(DocDataKeys.of(doc).data())
                .orElseGet(() -> new byte[0]),
            StandardCharsets.UTF_8
        );
    }

    @Override
    public NavigableMap<ValueNo, String> values(final DocId doc) {
        return this.parts.values().values(doc);
    }

    @Override
    public Optional<String> value(final DocId doc, final ValueNo slot) {
        return Optional.of(this.parts.values().value(doc, slot))
            .filter(found -> !found.isEmpty());
    }

    @Override
    public VidrioTermList terms(final DocId doc) {
        return this.parts.termlists().open(doc);
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
        return this.parts.metadata().get(key);
    }

    /**
     * Opens the values of a slot across documents.
     *
     * @param slot Slot number
     * @return Values in ascending document order
     */
    public ValueList openValueList(final ValueNo slot) {
        return this.parts.values().open(slot);
    }

    /**
     * Range of the document identifiers in use.
     *
     * @return Lowest and highest identifier of a present document
     */
    public DocRange usedDocIds() {
        final DatabaseStats stats = this.version.stats();
        final DocRange result;
        if (stats.last() == stats.docs()) {
            result = new DocRange(new DocId(1), new DocId((int) stats.last()));
        } else {
            result = this.parts.postings().get().docIdRange();
        }
        return result;
    }

    /**
     * Opens a document over a fetch of its parts.
     *
     * @param docid Document identifier
     * @param fetch Reads of the stored parts
     * @param lazy True to skip the presence check
     * @return Document, a not-found error when eager and absent
     */
    static Optional<DocumentInternal> document(
        final DocId docid, final DocumentFetch fetch, final boolean lazy
    ) {
        if (!lazy) {
            fetch.terms(docid);
        }
        return Optional.of(
            new DocumentInternal(docid, new VidrioDocument(docid, fetch))
        );
    }

    private static boolean storable(final String term) {
        return PostListKeys.bytes(term).length <= VidrioFormat.MAX_TERM_LENGTH;
    }
}
