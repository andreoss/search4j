/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the vidrio table format definitions.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.TermCount;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TermListTag}: the encoding as the reference writes it.
 *
 * @since 0.1.0
 */
final class TermListTagTest {

    @Test
    void encodesNothingForNoTerms() {
        Assertions.assertEquals(
            "", TermListTagTest.hex(Map.of()), "Empty tag"
        );
    }

    @Test
    void encodesLengthCountAndFirstTerm() {
        Assertions.assertEquals(
            "800101036162638001",
            TermListTagTest.hex(Map.of("abc", new TermCount(128))),
            "Length 128, one term, length byte, bytes, packed frequency"
        );
    }

    @Test
    void packsFrequencyIntoReuseByte() {
        final Map<String, TermCount> terms = new LinkedHashMap<>();
        terms.put("abd", new TermCount(2));
        terms.put("abc", new TermCount(1));
        terms.put("b", TermCount.ZERO);
        Assertions.assertEquals(
            "030303616263010e0164040162",
            TermListTagTest.hex(terms),
            "Terms in byte order, reuse 2 and wdf 2 as 14, reuse 0 and wdf 0 as 4"
        );
    }

    @Test
    void storesFrequencyAfterTermWhenTooLargeToPack() {
        final Map<String, TermCount> terms = new LinkedHashMap<>();
        terms.put("ab", new TermCount(127));
        terms.put("ac", new TermCount(126));
        terms.put("ad", new TermCount(83));
        terms.put("ae", new TermCount(84));
        terms.put("af", new TermCount(1000));
        Assertions.assertEquals(
            String.join(
                "", "8c0b05", "0261627f", "0101637e", "fd0164", "01016554",
                "010166e807"
            ),
            TermListTagTest.hex(terms),
            "Packed byte above 255 or wdf at least 127 take the long form"
        );
    }

    @Test
    void packsWhenTheReuseByteJustFits() {
        final Map<String, TermCount> terms = new LinkedHashMap<>();
        terms.put("x".repeat(126), new TermCount(3));
        terms.put("x".repeat(127), TermCount.ZERO);
        Assertions.assertEquals(
            String.format("03027e%s03fd0178", "78".repeat(126)),
            TermListTagTest.hex(terms),
            "Reuse 126 and wdf 0 pack to 253"
        );
    }

    @Test
    void encodesLongAndNonAsciiTerms() {
        final Map<String, TermCount> terms = new LinkedHashMap<>();
        terms.put("y".repeat(255), new TermCount(1));
        terms.put("é", new TermCount(2));
        Assertions.assertEquals(
            String.format("0302ff%s010002c3a902", "79".repeat(255)),
            TermListTagTest.hex(terms),
            "Term of 255 bytes, then a two-byte term with nothing in common"
        );
    }

    @Test
    void rejectsEmptyAndOverlongTerms() {
        Assertions.assertEquals(
            List.of(
                "Empty term in a term list",
                "Term too long for a term list: 256 bytes"
            ),
            List.of(
                TermListTagTest.failure(Map.of("", new TermCount(1))),
                TermListTagTest.failure(Map.of("z".repeat(256), new TermCount(1)))
            ),
            "Terms of one to 255 bytes only"
        );
    }

    private static String hex(final Map<String, TermCount> terms) {
        return HexFormat.of().formatHex(TermListTag.encode(terms));
    }

    private static String failure(final Map<String, TermCount> terms) {
        return Assertions.assertThrows(
            InvalidArgumentError.class, () -> TermListTag.encode(terms), "Rejected"
        ).getMessage();
    }
}
