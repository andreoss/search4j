/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the decider and external posting list operators.
 */
package io.github.andreoss.search4j.core.matcher;

import io.github.andreoss.search4j.core.api.Database;
import io.github.andreoss.search4j.core.api.DatabaseFactory;
import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.api.WritableDatabase;
import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Document decisions and external document sources.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule"
})
final class DeciderExternalPostListTest {

    @Test
    void keepsOnlyDocumentsTheDeciderAccepts() {
        final WritableDatabase database = DatabaseFactory.inMemory();
        final DocId keep = database.addDocument(
            DeciderExternalPostListTest.document("keep", "yes")
        );
        final DocId drop = database.addDocument(
            DeciderExternalPostListTest.document("drop", "no")
        );
        final PostList candidates = new DeciderExternalPostListTest.InMemoryAllDocuments(database);
        final PostList filtered = DeciderPostList.of(
            candidates, database,
            document -> "yes".equals(document.data())
        );
        final List<DocId> found = DeciderExternalPostListTest.drain(
            filtered
        );
        Assertions.assertEquals(
            List.of(keep), found, "Only accepted documents remain"
        );
        Assertions.assertFalse(
            found.contains(drop), "Rejected document is dropped"
        );
    }

    @Test
    void deciderReportsOneSubqueryAndDescription() {
        final WritableDatabase database = DatabaseFactory.inMemory();
        database.addDocument(DeciderExternalPostListTest.document("a", "x"));
        final PostList filtered = DeciderPostList.of(
            new DeciderExternalPostListTest.InMemoryAllDocuments(database), database,
            document -> true
        );
        Assertions.assertEquals(
            new TermCount(1), filtered.countMatchingSubqueries(),
            "One decider subquery"
        );
        Assertions.assertEquals(
            "DeciderPostList(InMemoryAllDocuments)",
            filtered.description(),
            "Description"
        );
    }

    @Test
    void externalSourceSuppliesOrderedDocuments() {
        final WritableDatabase database = DatabaseFactory.inMemory();
        final DocId first = database.addDocument(
            DeciderExternalPostListTest.document("a", "x")
        );
        final DocId second = database.addDocument(
            DeciderExternalPostListTest.document("b", "x")
        );
        final PostList external = new ExternalPostList(
            database, new DeciderExternalPostListTest.FixedSource(List.of(first, second)), 2.0
        );
        Assertions.assertEquals(
            List.of(first, second),
            DeciderExternalPostListTest.drain(external),
            "Documents in source order"
        );
        Assertions.assertEquals(
            4.0, external.recalcMaxWeight(), 1.0e-9,
            "Source bound scaled by the factor"
        );
        Assertions.assertEquals(
            "ExternalPostList(2)",
            external.description(),
            "Description"
        );
    }

    @Test
    void externalSourceSkipsToATarget() {
        final WritableDatabase database = DatabaseFactory.inMemory();
        final DocId first = database.addDocument(
            DeciderExternalPostListTest.document("a", "x")
        );
        final DocId second = database.addDocument(
            DeciderExternalPostListTest.document("b", "x")
        );
        final PostList external = new ExternalPostList(
            database, new DeciderExternalPostListTest.FixedSource(List.of(first, second)), 1.0
        );
        Assertions.assertEquals(
            second,
            external.skipTo(second, 0.0).docId(),
            "Skip to the second document"
        );
    }

    @Test
    void rejectsNullParts() {
        final WritableDatabase database = DatabaseFactory.inMemory();
        final PostList candidates = new DeciderExternalPostListTest.InMemoryAllDocuments(database);
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> DeciderPostList.of(candidates, database, null),
            "Decider is required"
        );
        Assertions.assertThrows(
            InvalidArgumentError.class,
            () -> new ExternalPostList(database, null, 1.0),
            "Source is required"
        );
    }

    private static Document document(final String term, final String data) {
        final Document doc = DatabaseFactory.document();
        doc.addTerm(term, new TermCount(1));
        doc.setData(data);
        return doc;
    }

    private static List<DocId> drain(final PostList source) {
        PostList list = source;
        final List<DocId> result = new ArrayList<>(0);
        while (!list.atEnd()) {
            list = list.next();
            if (!list.atEnd()) {
                result.add(list.docId());
            }
        }
        return result;
    }

    /**
     * Every document of a database, in identifier order.
     *
     * @since 0.1.0
     */
    private static final class InMemoryAllDocuments implements PostList {

        /**
         * Database read.
         */
        private final Database database;

        /**
         * Current document, absent before the first move and after the end.
         */
        private DocId current;

        /**
         * Creates the list.
         *
         * @param database Database read
         */
        InMemoryAllDocuments(final Database database) {
            this.database = database;
        }

        @Override
        public DocId docId() {
            return this.current;
        }

        @Override
        public boolean atEnd() {
            return this.current != null
                && this.current.compareTo(this.database.lastDocId()) >= 0;
        }

        @Override
        public PostList next(final double least) {
            if (this.current == null) {
                this.current = DocId.of(1);
            } else if (this.current.compareTo(this.database.lastDocId()) < 0) {
                this.current = this.current.next();
            } else {
                this.current = this.database.lastDocId().next();
            }
            return this;
        }

        @Override
        public PostList skipTo(final DocId target, final double least) {
            if (target.compareTo(this.database.lastDocId()) > 0) {
                this.current = this.database.lastDocId().next();
            } else {
                this.current = target;
            }
            return this;
        }

        @Override
        public TermCount termFrequency() {
            return this.database.docCount();
        }

        @Override
        public double weight(final DocStats doc) {
            return 0.0;
        }

        @Override
        public double recalcMaxWeight() {
            return 0.0;
        }

        @Override
        public TermCount countMatchingSubqueries() {
            return new TermCount(1);
        }

        @Override
        public String description() {
            return "InMemoryAllDocuments";
        }
    }

    /**
     * Source returning fixed documents and weights.
     *
     * @since 0.1.0
     */
    private static final class FixedSource implements PostingSource {

        /**
         * Documents returned.
         */
        private final List<DocId> documents;

        /**
         * Index of the current document, minus one before the start.
         */
        private int index;

        /**
         * Creates the source.
         *
         * @param documents Documents returned
         */
        FixedSource(final List<DocId> documents) {
            this.documents = documents;
            this.index = -1;
        }

        @Override
        public void reset(final Database database, final int shard) {
            this.index = -1;
        }

        @Override
        public void next(final double least) {
            this.index += 1;
        }

        @Override
        public void skipTo(final DocId target, final double least) {
            if (this.index < 0) {
                this.index = 0;
            }
            while (this.index < this.documents.size()
                && this.documents.get(this.index).compareTo(target) < 0) {
                this.index += 1;
            }
        }

        @Override
        public boolean atEnd() {
            return this.index >= this.documents.size();
        }

        @Override
        public DocId docId() {
            return this.documents.get(this.index);
        }

        @Override
        public double weight() {
            return 1.0;
        }

        @Override
        public double maxWeight() {
            return 2.0;
        }

        @Override
        public TermCount termFrequencyLower() {
            return new TermCount(this.documents.size());
        }

        @Override
        public TermCount termFrequencyEstimate() {
            return new TermCount(this.documents.size());
        }

        @Override
        public TermCount termFrequencyUpper() {
            return new TermCount(this.documents.size());
        }
    }
}
