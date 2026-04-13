/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.backend.list.DocStats;
import io.github.andreoss.search4j.core.backend.list.TermWeight;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.TotalLength;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the reference rules the shared corpus cannot reach: a term with
 * no possible occurrence weighs nothing, and unknown tf-idf codes are
 * refused.
 *
 * @since 0.1.0
 */
final class WeightSchemesTest {

    @ParameterizedTest
    @ValueSource(strings = {"pl2", "pl2+", "ineb2", "ifb2", "inl2", "dph", "dlh", "bb2"})
    void weighsNothingWithoutPossibleOccurrence(final String scheme) {
        final TermWeight part = Weight.create(scheme, new Registry()).term(
            new WeightStats(
                new CollectionStats(
                    new TermCount(4), TermCount.ZERO, new TotalLength(9L),
                    new TermCount(2),
                    new LengthBounds(TermCount.ONE, new TermCount(9), TermCount.ONE)
                ),
                new TermStats(
                    new TermCount(2), TermCount.ZERO, new TermCount(3),
                    TermCount.ONE, TermCount.ZERO
                )
            ),
            1.0
        );
        Assertions.assertEquals(
            List.of(0.0, 0.0),
            List.of(
                part.maxPart(),
                part.sumPart(
                    new TermCount(2),
                    new DocStats(new TermCount(3), new TermCount(2), new TermCount(2))
                )
            ),
            "Upper bound zero, no weight"
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "9a9999999999c93f000000000000f03f000201",
            "9a9999999999c93f000000000000f03f0c0201",
            "9a9999999999c93f000000000000f03f010001",
            "9a9999999999c93f000000000000f03f010b01",
            "9a9999999999c93f000000000000f03f010200",
            "9a9999999999c93f000000000000f03f010202"
        }
    )
    void keepsUnknownNormalisationCodes(final String hex) {
        Assertions.assertEquals(
            hex,
            HexFormat.of().formatHex(
                new TfidfWeight().unserialise(HexFormat.of().parseHex(hex)).serialise()
            ),
            "Code outside the normalisations"
        );
    }

    @Test
    void leavesTheFrequencyForAnUnknownFrequencyCode() {
        Assertions.assertEquals(
            WeightSchemesTest.unknown("010201"), WeightSchemesTest.unknown("0c0201"),
            "Unknown frequency code weighs as the raw frequency"
        );
    }

    @Test
    void invertsTheDocumentFrequencyForAnUnknownCode() {
        Assertions.assertEquals(
            3.0 * Math.log(1.0 / 2.0), WeightSchemesTest.unknown("010b01"), 1e-12,
            "Unknown document frequency code weighs log of one over the frequency"
        );
    }

    @Test
    void parsesThreeLetterNormalisations() {
        Assertions.assertArrayEquals(
            new TfidfWeight("Pfn").serialise(),
            new TfidfWeight(
                new TfidfNorms(WdfNorm.PIVOTED, IdfNorm.FREQ, WtNorm.NONE)
            ).serialise(),
            "Letters name the normalisations"
        );
    }

    private static double unknown(final String codes) {
        return new TfidfWeight().unserialise(
            HexFormat.of().parseHex(String.format("9a9999999999c93f000000000000f03f%s", codes))
        ).term(
            new WeightStats(
                new CollectionStats(
                    new TermCount(4), TermCount.ZERO, new TotalLength(9L),
                    new TermCount(2),
                    new LengthBounds(TermCount.ONE, new TermCount(9), TermCount.ONE)
                ),
                new TermStats(
                    new TermCount(2), TermCount.ZERO, new TermCount(3),
                    TermCount.ONE, new TermCount(5)
                )
            ),
            1.0
        ).sumPart(
            new TermCount(3),
            new DocStats(new TermCount(6), new TermCount(2), new TermCount(3))
        );
    }
}
