/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

/**
 * A document: terms with positions, values in numbered slots, and opaque data.
 *
 * <p>Terms carry a within-document frequency and an ascending set of
 * positions. Empty term text is rejected with an invalid-argument error.</p>
 *
 * @since 0.1.0
 */
public interface Document extends DocumentValues, DocumentTerms {

    /**
     * Opaque data of the document.
     *
     * @return Data, empty when never set
     */
    String data();

    /**
     * Replaces the opaque data of the document.
     *
     * @param data New data, not null
     */
    void setData(String data);
}
