/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.api.Document;
import io.github.andreoss.search4j.core.error.InvalidOperationError;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.DocId;

/**
 * Document and metadata changes a writable shard accepts.
 *
 * <p>A read-only shard keeps the defaults, which raise an invalid-operation
 * error.</p>
 *
 * @since 0.1.0
 */
public interface ShardWrites {

    /**
     * Adds a copy of a document under the next identifier.
     *
     * @param document Document to copy in
     * @return New identifier
     */
    default DocId addDocument(final Document document) {
        throw new InvalidOperationError(
            "Adding a document needs a writable shard"
        );
    }

    /**
     * Deletes a document.
     *
     * @param docid Identifier of a present document
     */
    default void deleteDocument(final DocId docid) {
        throw new InvalidOperationError(
            "Deleting a document needs a writable shard"
        );
    }

    /**
     * Stores a copy of a document at an identifier, adding it when absent.
     *
     * @param docid Non-zero document identifier
     * @param document Document to copy in
     */
    default void replaceDocument(final DocId docid, final Document document) {
        throw new InvalidOperationError(
            "Replacing a document needs a writable shard"
        );
    }

    /**
     * Metadata stored under a key.
     *
     * @param key Non-empty key
     * @return Value, empty when unset
     */
    default String metadata(final String key) {
        return "";
    }

    /**
     * Stores metadata under a key; an empty value removes the key.
     *
     * @param key Non-empty key
     * @param value Value to store, empty to remove
     */
    default void setMetadata(final String key, final String value) {
        throw new UnimplementedError(
            "This backend does not implement metadata"
        );
    }

    /**
     * Makes pending changes durable.
     */
    default void commit() {
        throw new InvalidOperationError("Committing needs a writable shard");
    }

    /**
     * Discards pending changes.
     */
    default void cancel() {
        throw new InvalidOperationError("Cancelling needs a writable shard");
    }
}
