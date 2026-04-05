/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the stemming registry and the Porter stemmer.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests of {@link Stem} and {@link PorterStemmer} against the published
 * Porter vocabulary.
 *
 * @since 0.1.0
 */
final class StemTest {

    @ParameterizedTest
    @MethodSource("porter")
    void stemsWithPorter(final String word, final String expected) {
        Assertions.assertEquals(
            expected, Stem.of("porter").stem(word), word
        );
    }

    @Test
    void listsThePorterLanguage() {
        Assertions.assertTrue(
            Stem.availableLanguages().contains("porter"), "Porter listed"
        );
    }

    @Test
    void reportsAvailabilityIgnoringCase() {
        Assertions.assertTrue(Stem.isAvailable("PORTER"), "Case-insensitive");
    }

    @Test
    void reportsAMissingLanguageAsUnavailable() {
        Assertions.assertFalse(
            Stem.isAvailable("klingon"), "Unknown language"
        );
    }

    @Test
    void keepsWordsWithTheIdentityStemmer() {
        Assertions.assertEquals(
            "Running", Stem.of("none").stem("Running"), "Unchanged word"
        );
    }

    @Test
    void reportsTheSelectedLanguage() {
        Assertions.assertEquals(
            "porter", Stem.of("PORTER").name(), "Language name"
        );
    }

    @Test
    void rejectsAnUnknownLanguage() {
        Assertions.assertThrows(
            InvalidArgumentError.class, () -> Stem.of("klingon"),
            "Unknown language"
        );
    }

    @Test
    void rejectsAnEmptyNameAsKnown() {
        Assertions.assertEquals(
            "none", Stem.of("").name(), "Empty name is identity"
        );
    }

    @Test
    void exposesTheRegistryAsAnImmutableList() {
        final List<String> names = Stem.availableLanguages();
        Assertions.assertThrows(
            UnsupportedOperationException.class, () -> names.add("klingon"),
            "Immutable list"
        );
    }

    static Stream<Arguments> porter() {
        return Stream.of(
            Arguments.of("caresses", "caress"),
            Arguments.of("ponies", "poni"),
            Arguments.of("ties", "ti"),
            Arguments.of("caress", "caress"),
            Arguments.of("cats", "cat"),
            Arguments.of("feed", "feed"),
            Arguments.of("agreed", "agre"),
            Arguments.of("plastered", "plaster"),
            Arguments.of("bled", "bled"),
            Arguments.of("motoring", "motor"),
            Arguments.of("sing", "sing"),
            Arguments.of("conflated", "conflat"),
            Arguments.of("troubled", "troubl"),
            Arguments.of("sized", "size"),
            Arguments.of("hopping", "hop"),
            Arguments.of("tanned", "tan"),
            Arguments.of("falling", "fall"),
            Arguments.of("hissing", "hiss"),
            Arguments.of("fizzed", "fizz"),
            Arguments.of("failing", "fail"),
            Arguments.of("filing", "file"),
            Arguments.of("happy", "happi"),
            Arguments.of("sky", "sky"),
            Arguments.of("relational", "relat"),
            Arguments.of("conditional", "condit"),
            Arguments.of("rational", "ration"),
            Arguments.of("valenci", "valenc"),
            Arguments.of("hesitanci", "hesit"),
            Arguments.of("digitizer", "digit"),
            Arguments.of("conformabli", "conform"),
            Arguments.of("radicalli", "radic"),
            Arguments.of("differentli", "differ"),
            Arguments.of("vileli", "vile"),
            Arguments.of("analogousli", "analog"),
            Arguments.of("vietnamization", "vietnam"),
            Arguments.of("predication", "predic"),
            Arguments.of("operator", "oper"),
            Arguments.of("feudalism", "feudal"),
            Arguments.of("decisiveness", "decis"),
            Arguments.of("hopefulness", "hope"),
            Arguments.of("callousness", "callous"),
            Arguments.of("formaliti", "formal"),
            Arguments.of("sensitiviti", "sensit"),
            Arguments.of("sensibiliti", "sensibl"),
            Arguments.of("triplicate", "triplic"),
            Arguments.of("formative", "form"),
            Arguments.of("formalize", "formal"),
            Arguments.of("electriciti", "electr"),
            Arguments.of("electrical", "electr"),
            Arguments.of("hopeful", "hope"),
            Arguments.of("goodness", "good"),
            Arguments.of("revival", "reviv"),
            Arguments.of("allowance", "allow"),
            Arguments.of("inference", "infer"),
            Arguments.of("airliner", "airlin"),
            Arguments.of("gyroscopic", "gyroscop"),
            Arguments.of("adjustable", "adjust"),
            Arguments.of("defensible", "defens"),
            Arguments.of("irritant", "irrit"),
            Arguments.of("replacement", "replac"),
            Arguments.of("adjustment", "adjust"),
            Arguments.of("dependent", "depend"),
            Arguments.of("adoption", "adopt"),
            Arguments.of("homologou", "homolog"),
            Arguments.of("communism", "commun"),
            Arguments.of("activate", "activ"),
            Arguments.of("angulariti", "angular"),
            Arguments.of("homologous", "homolog"),
            Arguments.of("effective", "effect"),
            Arguments.of("bowdlerize", "bowdler"),
            Arguments.of("probate", "probat"),
            Arguments.of("rate", "rate"),
            Arguments.of("cease", "ceas"),
            Arguments.of("controll", "control"),
            Arguments.of("roll", "roll")
        );
    }
}
