/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Iterator bases shared by the database backends.
 */
package io.github.andreoss.search4j.core.backend.list;

import io.github.andreoss.search4j.core.types.DocId;

/**
 * Forward movement over the documents of a posting list.
 *
 * <p>The list starts before its first document, so a move must come before
 * any call that needs a current document. A move returns the list to carry
 * on with: the same list, or a cheaper replacement the caller adopts.</p>
 *
 * @since 0.1.0
 */
public interface PostingCursor {

    /**
     * Document the list is on.
     *
     * @return Current document identifier
     */
    DocId docId();

    /**
     * Tests whether the list is exhausted.
     *
     * @return True once moved past the last document
     */
    boolean atEnd();

    /**
     * Moves to the next document.
     *
     * @param least Lowest weight contribution still wanted, a hint only
     * @return List to continue with
     */
    PostList next(double least);

    /**
     * Moves to the first document at or after a target, never backwards.
     *
     * @param target Lowest document wanted
     * @param least Lowest weight contribution still wanted, a hint only
     * @return List to continue with
     */
    PostList skipTo(DocId target, double least);

    /**
     * Checks whether a present document is in the list, moving only when
     * that is cheap.
     *
     * @param target Document known to exist in the database
     * @param least Lowest weight contribution still wanted, a hint only
     * @return List to continue with and whether the position is valid
     */
    default Probe check(final DocId target, final double least) {
        return new Probe(this.skipTo(target, least), true);
    }

    /**
     * Moves to the next document, accepting any weight.
     *
     * @return List to continue with
     */
    default PostList next() {
        return this.next(0.0);
    }

    /**
     * Moves to the first document at or after a target, accepting any weight.
     *
     * @param target Lowest document wanted
     * @return List to continue with
     */
    default PostList skipTo(final DocId target) {
        return this.skipTo(target, 0.0);
    }

    /**
     * Human-readable description for diagnostics.
     *
     * @return Description naming the list and its state
     */
    String description();
}
