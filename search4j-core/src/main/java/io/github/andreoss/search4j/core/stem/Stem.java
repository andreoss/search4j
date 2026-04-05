/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Snowball stemming runtime and language registry.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.termgen.Stemmer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stemmer of a named language.
 *
 * <p>The registry names the stemming algorithms this build ships; the
 * algorithms of the Snowball distribution are added by the stemming
 * iterations. The name {@code none} selects the identity stemmer.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class Stem implements Stemmer {

    /**
     * Name of the identity stemmer.
     */
    private static final String NONE = "none";

    /**
     * Algorithms by name.
     */
    private static final Map<String, Supplier<Stemmer>> LANGUAGES =
        Stem.registry();

    /**
     * Selected algorithm.
     */
    private final Stemmer stemmer;

    /**
     * Name of the selected language.
     */
    private final String language;

    /**
     * Creates a stemmer over an algorithm.
     *
     * @param stemmer Selected algorithm
     * @param language Name of the language
     */
    private Stem(final Stemmer stemmer, final String language) {
        this.stemmer = stemmer;
        this.language = language;
    }

    /**
     * Selects the stemmer of a language.
     *
     * @param language Language name, case-insensitive; {@code none} for the
     *  identity stemmer
     * @return Stemmer of the language
     */
    public static Stem of(final String language) {
        final String key = language.toLowerCase(Locale.ROOT);
        final Stem result;
        if (Stem.NONE.equals(key) || key.isEmpty()) {
            result = new Stem(Stemmer.NONE, Stem.NONE);
        } else {
            final Supplier<Stemmer> found = Stem.LANGUAGES.get(key);
            if (found == null) {
                throw new InvalidArgumentError(
                    String.format("Unknown stemming language: %s", language)
                );
            }
            result = new Stem(found.get(), key);
        }
        return result;
    }

    /**
     * Names of the available stemming languages.
     *
     * @return Language names in ascending order
     */
    public static List<String> availableLanguages() {
        final List<String> names = new ArrayList<>(Stem.LANGUAGES.keySet());
        Collections.sort(names);
        return List.copyOf(names);
    }

    /**
     * Reports whether a stemming language is available.
     *
     * @param language Language name, case-insensitive
     * @return True when the algorithm is shipped
     */
    public static boolean isAvailable(final String language) {
        return Stem.LANGUAGES.containsKey(language.toLowerCase(Locale.ROOT));
    }

    @Override
    public String stem(final String word) {
        return this.stemmer.stem(word);
    }

    @Override
    public String name() {
        return this.language;
    }

    private static Map<String, Supplier<Stemmer>> registry() {
        return Map.of("porter", PorterStemmer::new);
    }
}
