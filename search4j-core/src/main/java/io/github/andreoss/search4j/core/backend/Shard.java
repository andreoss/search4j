/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.backend.list.PostList;
import io.github.andreoss.search4j.core.backend.list.ValueList;
import io.github.andreoss.search4j.core.error.DatabaseLockError;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.types.Backend;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.ValueNo;

/**
 * Contract every database backend fulfils: one shard of a database.
 *
 * <p>The public database handle validates arguments before calling here.
 * Defaults cover what a backend need not specialise.</p>
 *
 * @since 0.1.0
 */
public interface Shard extends ShardStatistics, ShardBounds, ShardListings,
    ShardDictionaries, ShardWrites, ShardLifecycle {

    /**
     * Backend kind of the shard.
     *
     * @return Backend
     */
    Backend backend();

    /**
     * Opens the values of a slot across documents.
     *
     * <p>The default reads every document in turn.</p>
     *
     * @param slot Slot to read
     * @return Values in ascending document order
     */
    default ValueList openValueList(final ValueNo slot) {
        return new SlowValueList(this, slot);
    }

    /**
     * Deletes every document indexed by a term.
     *
     * @param term Non-empty term; an unknown term deletes nothing
     */
    default void deleteDocument(final String term) {
        if (this.transactions().state() == TransactionState.READ_ONLY) {
            throw new InvalidOperationError(
                "Deleting a document needs a writable shard"
            );
        }
        this.transactions().batch(
            () -> {
                PostList list = this.openPostList(term).next();
                while (!list.atEnd()) {
                    this.deleteDocument(list.docId());
                    list = list.next();
                }
                return list;
            }
        );
    }

    /**
     * Replaces the first document indexed by a term and deletes the others,
     * or adds the document when the term indexes none.
     *
     * @param term Non-empty term
     * @param document Document to copy in
     * @return Identifier stored at
     */
    default DocId replaceDocument(final String term, final Document document) {
        if (this.transactions().state() == TransactionState.READ_ONLY) {
            throw new InvalidOperationError(
                "Replacing a document needs a writable shard"
            );
        }
        final PostList first = this.openPostList(term).next();
        final DocId result;
        if (first.atEnd()) {
            result = this.addDocument(document);
        } else {
            result = first.docId();
            this.transactions().batch(
                () -> {
                    this.replaceDocument(result, document);
                    PostList list = first.next();
                    while (!list.atEnd()) {
                        this.deleteDocument(list.docId());
                        list = list.next();
                    }
                    return list;
                }
            );
        }
        return result;
    }

    /**
     * Opens a transaction.
     *
     * @param flushed True to commit pending changes first
     */
    default void beginTransaction(final boolean flushed) {
        this.transactions().begin(flushed, this::commit);
    }

    /**
     * Closes the open transaction.
     *
     * @param keep True to commit its changes, false to cancel them
     */
    default void endTransaction(final boolean keep) {
        this.transactions().end(keep, this::commit, this::cancel);
    }

    /**
     * Returns a shard holding the lock wanted.
     *
     * @param writable True to lock for writing
     * @return This shard when read-only access is wanted
     */
    default Shard relock(final boolean writable) {
        if (writable) {
            throw new DatabaseLockError("Not possible to lock for writing");
        }
        return this;
    }

    /**
     * Human-readable description for diagnostics.
     *
     * @return Description naming the backend
     */
    String description();
}
