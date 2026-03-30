/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.backend.list.DocRange;
import io.github.andreoss.search4j.core.error.UnimplementedError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.Optional;

/**
 * Lifecycle, identity and housekeeping of a shard.
 *
 * @since 0.1.0
 */
public interface ShardLifecycle {

    /**
     * Transaction state machine of the shard.
     *
     * @return State machine, shared by every call
     */
    Transactions transactions();

    /**
     * Moves to the latest revision, when the backend can.
     *
     * @return True when the shard may have changed
     */
    default boolean reopen() {
        return false;
    }

    /**
     * Releases the shard; later calls raise a database-closed error.
     */
    void close();

    /**
     * Revision of the shard.
     *
     * @return Revision number
     */
    default Revision revision() {
        throw new UnimplementedError(
            "This backend does not provide revision information"
        );
    }

    /**
     * Unique identifier of the shard.
     *
     * @return Identifier, empty when the backend has none
     */
    default Optional<Uuid> uuid() {
        return Optional.empty();
    }

    /**
     * Range of identifiers in use.
     *
     * @return Lowest and highest identifier of a present document
     */
    default DocRange usedDocIds() {
        throw new UnimplementedError(
            "This backend does not report the identifiers in use"
        );
    }

    /**
     * Tests whether a writer holds the lock of the shard.
     *
     * @return True when locked for writing
     */
    default boolean locked() {
        return false;
    }

    /**
     * Number of shards this one stands for.
     *
     * @return One for a plain shard, zero for none
     */
    TermCount size();

    /**
     * Signals that a document opened from this shard is no longer used.
     *
     * @param document Document opened from this shard
     */
    void release(DocumentInternal document);
}
