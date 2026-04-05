/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Term generation from text: words, prefixes, positions and n-grams.
 */
package io.github.andreoss.search4j.core.termgen;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a term is too common to index.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Stopper {

    /**
     * Words rejected.
     */
    private final Set<String> words;

    /**
     * Stopper with an explicit word list.
     *
     * @param words Words to reject
     */
    public Stopper(final String... words) {
        this(Stopper.lower(Set.of(words)));
    }

    private Stopper(final Set<String> words) {
        this.words = words;
    }

    /**
     * Stopper holding the given words and no others.
     *
     * @param words Words to reject
     * @return Stopper over the words
     */
    public static Stopper of(final Collection<String> words) {
        return new Stopper(Stopper.lower(new HashSet<>(words)));
    }

    /**
     * Stopper holding the given words and no others.
     *
     * @param words Words to reject
     * @return Stopper over the words
     */
    public static Stopper of(final String... words) {
        return new Stopper(words);
    }

    /**
     * Tests whether a term is a stop word.
     *
     * <p>Terms of fewer than three characters, or whose first character is a
     * digit, are never stop words.</p>
     *
     * @param term Term to test
     * @return True when the term is rejected
     */
    public boolean stop(final String term) {
        final boolean result;
        if (term.length() < 3 || Character.isDigit(term.codePointAt(0))) {
            result = false;
        } else {
            result = this.words.contains(term.toLowerCase(Locale.ROOT));
        }
        return result;
    }

    /**
     * Words held by this stopper.
     *
     * @return Unmodifiable set of the words, in lower case
     */
    public Set<String> words() {
        return this.words;
    }

    private static Set<String> lower(final Collection<String> words) {
        final Set<String> found = new HashSet<>(words.size());
        for (final String word : words) {
            found.add(word.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(found);
    }
}
