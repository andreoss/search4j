/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Posting list operators combining the streams of a query tree.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.EmptyPostList;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.common.Strings;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Objects;

/**
 * Documents supplied by an external posting source.
 *
 * <p>The list starts before its first document: the source is reset on
 * construction and its first document is taken by the first move.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.NullAssignment")
public final class ExternalPostList implements PostList {

    /**
     * Source of documents and weights.
     */
    private final PostingSource source;

    /**
     * Weight factor applied to every document.
     */
    private final double factor;

    /**
     * Current document, absent before the first move and after the end.
     */
    private DocId current;

    /**
     * Whether the source has been moved at least once.
     */
    private boolean started;

    /**
     * Creates the external list.
     *
     * @param database Database read
     * @param source Source of documents and weights
     * @param factor Weight factor applied to every document
     */
    public ExternalPostList(
        final Database database, final PostingSource source,
        final double factor
    ) {
        this(ExternalPostList.prepare(database, source, factor));
    }

    /**
     * Creates the external list from checked parts.
     *
     * @param parts Database, source and factor
     */
    private ExternalPostList(final SourceParts parts) {
        this.source = parts.source();
        this.factor = parts.factor();
        this.current = null;
        this.started = false;
    }

    /**
     * Creates the external list and resets its source.
     *
     * @param database Database read
     * @param source Source of documents and weights
     * @param factor Weight factor applied to every document
     * @return External posting list
     */
    public static ExternalPostList of(
        final Database database, final PostingSource source,
        final double factor
    ) {
        return new ExternalPostList(database, source, factor);
    }

    /**
     * Validates the arguments and resets the source.
     *
     * @param database Database read
     * @param source Source of documents and weights
     * @param factor Weight factor applied to every document
     * @return Checked parts
     */
    private static SourceParts prepare(
        final Database database, final PostingSource source,
        final double factor
    ) {
        if (database == null) {
            throw new NullPointerException("database");
        }
        if (source == null) {
            throw new NullPointerException("source");
        }
        source.reset(database, 0);
        return new SourceParts(source, factor);
    }

    /**
     * Source and weight factor.
     *
     * @param source Source of documents and weights
     * @param factor Weight factor applied to every document
     * @since 0.1.0
     */
    private record SourceParts(PostingSource source, double factor) {
    }

    @Override
    public DocId docId() {
        if (this.current == null) {
            throw new InvalidOperationError(
                "An external list is not on a document"
            );
        }
        return this.current;
    }

    @Override
    public boolean atEnd() {
        return this.started && this.source.atEnd();
    }

    @Override
    public PostList next(final double least) {
        this.source.next(least);
        this.started = true;
        return this.advance();
    }

    @Override
    public PostList skipTo(final DocId target, final double least) {
        PostList result = this;
        if (this.current == null
            || target.compareTo(this.current) > 0) {
            this.source.skipTo(target, least);
            this.started = true;
            result = this.advance();
        }
        return result;
    }

    @Override
    public TermCount termFrequency() {
        return this.source.termFrequencyEstimate();
    }

    @Override
    public TermCount wdf() {
        return TermCount.ZERO;
    }

    @Override
    public double weight(final DocStats doc) {
        return this.factor * this.source.weight();
    }

    @Override
    public double recalcMaxWeight() {
        return this.factor * this.source.maxWeight();
    }

    @Override
    public TermCount countMatchingSubqueries() {
        return TermCount.ZERO;
    }

    @Override
    public String description() {
        return String.format(
            "ExternalPostList(%s)", Strings.decimal(this.factor)
        );
    }

    private PostList advance() {
        PostList result = this;
        if (this.source.atEnd()) {
            this.current = null;
            result = EmptyPostList.INSTANCE;
        } else {
            this.current = this.source.docId();
        }
        return result;
    }
}
