/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.andreoss.search4j.core.compat.differential;

import io.github.andreoss.search4j.core.stem.Stem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Compares the shipped stemmers with the reference vocabulary and output
 * for every language.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class StemmingDifferentialTest {

    /**
     * Vocabulary and expected output of the shipped languages.
     */
    private static final List<String> LANGUAGES = List.of("porter");

    @Test
    void stemsEveryVocableAsTheReference() throws IOException {
        Assumptions.assumeTrue(NativeTools.available(), "reference not held");
        final List<String> faults = new ArrayList<>(0);
        for (final String language : StemmingDifferentialTest.LANGUAGES) {
            final Path base = Path.of(
                "..", "scratch", "reference-2.1.0", "source",
                "data", "stemming", language
            );
            final List<String> vocab =
                Files.readAllLines(base.resolve("voc.txt"), StandardCharsets.UTF_8);
            final List<String> output =
                Files.readAllLines(base.resolve("output.txt"), StandardCharsets.UTF_8);
            final Stem stem = Stem.of(language);
            for (int idx = 0; idx < vocab.size(); ++idx) {
                final String word = vocab.get(idx);
                final String expected = output.get(idx);
                final String actual = stem.stem(word);
                if (!expected.equals(actual)) {
                    faults.add(
                        String.format(
                            "%s: %s -> %s, expected %s",
                            language, word, actual, expected
                        )
                    );
                }
            }
        }
        Assertions.assertEquals(
            List.of(), faults.stream().limit(20).toList(),
            String.format("%d stem mismatches", faults.size())
        );
    }
}
