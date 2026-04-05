/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Named factories of weighting schemes and range processors.
 *
 * <p>A registry starts with the schemes this build ships; a caller may add
 * its own under a new name. Names are compared case-insensitively.</p>
 *
 * @since 0.1.0
 */
public final class Registry {

    /**
     * Factories present in a fresh registry.
     */
    private static final Map<String, Supplier<Weight>> DEFAULTS =
        Registry.defaults();

    /**
     * Weighting schemes by name.
     */
    private final Map<String, Supplier<Weight>> weights;

    /**
     * Range processors by name.
     */
    private final Map<String, Supplier<RangeProcessor>> ranges;

    /**
     * Creates a registry holding the default schemes.
     */
    public Registry() {
        this.weights = new HashMap<>(Registry.DEFAULTS);
        this.ranges = new HashMap<>(4);
    }

    /**
     * Registers a weighting scheme.
     *
     * @param name Name of the scheme, case-insensitive
     * @param factory Factory of fresh scheme instances
     */
    public void registerWeightingScheme(
        final String name, final Supplier<Weight> factory
    ) {
        this.weights.put(Registry.key(name), factory);
    }

    /**
     * Creates a weighting scheme by name.
     *
     * @param name Name of the scheme, case-insensitive
     * @return Fresh scheme instance
     */
    public Weight getWeightingScheme(final String name) {
        final Supplier<Weight> found = this.weights.get(Registry.key(name));
        if (found == null) {
            throw new InvalidArgumentError(
                String.format("Unknown weighting scheme: %s", name)
            );
        }
        return found.get();
    }

    /**
     * Reports whether a weighting scheme is registered.
     *
     * @param name Name of the scheme, case-insensitive
     * @return True when the name is known
     */
    public boolean hasWeightingScheme(final String name) {
        return this.weights.containsKey(Registry.key(name));
    }

    /**
     * Names of the registered weighting schemes.
     *
     * @return Names in ascending order
     */
    public List<String> weightingSchemes() {
        final List<String> names = new ArrayList<>(this.weights.keySet());
        Collections.sort(names);
        return List.copyOf(names);
    }

    /**
     * Registers a range processor.
     *
     * @param name Name of the processor, case-insensitive
     * @param factory Factory of fresh processors
     */
    public void registerRangeProcessor(
        final String name, final Supplier<RangeProcessor> factory
    ) {
        this.ranges.put(Registry.key(name), factory);
    }

    /**
     * Creates a range processor by name.
     *
     * @param name Name of the processor, case-insensitive
     * @return Fresh processor instance
     */
    public RangeProcessor getRangeProcessor(final String name) {
        final Supplier<RangeProcessor> found =
            this.ranges.get(Registry.key(name));
        if (found == null) {
            throw new InvalidArgumentError(
                String.format("Unknown range processor: %s", name)
            );
        }
        return found.get();
    }

    /**
     * Reports whether a range processor is registered.
     *
     * @param name Name of the processor, case-insensitive
     * @return True when the name is known
     */
    public boolean hasRangeProcessor(final String name) {
        return this.ranges.containsKey(Registry.key(name));
    }

    private static String key(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Supplier<Weight>> defaults() {
        final Map<String, Supplier<Weight>> map = new HashMap<>(16);
        map.put("bool", BoolWeight::new);
        map.put("coord", CoordWeight::new);
        map.put("bm25", BM25Weight::new);
        map.put("bm25plus", BM25PlusWeight::new);
        map.put("pl2", PL2Weight::new);
        map.put("pl2plus", PL2PlusWeight::new);
        map.put("ineb2", IneB2Weight::new);
        map.put("ifb2", IfB2Weight::new);
        map.put("inl2", InL2Weight::new);
        map.put("dph", DphWeight::new);
        map.put("dlh", DlhWeight::new);
        map.put("bb2", BB2Weight::new);
        map.put("lm", LmWeight::new);
        map.put("tfidf", TfidfWeight::new);
        map.put("dice", DiceWeight::new);
        return map;
    }
}
