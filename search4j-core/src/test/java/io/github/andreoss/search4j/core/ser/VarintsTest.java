/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link Varints}.
 *
 * @since 0.1.0
 */
final class VarintsTest {

    @Test
    void roundTripsZero() {
        Assertions.assertEquals(
            0L, Varints.unpack(Varints.pack(0L)), "Zero round trip"
        );
    }

    @Test
    void roundTripsSmallValue() {
        Assertions.assertEquals(
            127L, Varints.unpack(Varints.pack(127L)), "Small round trip"
        );
    }

    @Test
    void roundTripsMultiByteValue() {
        Assertions.assertEquals(
            300L, Varints.unpack(Varints.pack(300L)), "Multi-byte round trip"
        );
    }

    @Test
    void roundTripsLargeValue() {
        Assertions.assertEquals(
            1_000_000L,
            Varints.unpack(Varints.pack(1_000_000L)),
            "Large round trip"
        );
    }

    @Test
    void packsUnsignedCountsAsTheReference() {
        Assertions.assertEquals(
            List.of("80bcc1960b", "ffffffff0f", 3_000_000_000L),
            List.of(
                HexFormat.of().formatHex(Varints.pack(3_000_000_000L)),
                HexFormat.of().formatHex(Varints.pack(4_294_967_295L)),
                Varints.unpack(HexFormat.of().parseHex("80bcc1960b"))
            ),
            "Bytes of wdfs above 2^31 in the reference tables"
        );
    }

    @Test
    void rejectsNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> Varints.pack(-1L),
            "Negative rejected"
        );
    }

    @Test
    void packsUnsignedSixtyFourBitValues() {
        Assertions.assertEquals(
            List.of(
                "80808080808080808001", "ffffffffffffffffff01", "05"
            ),
            List.of(
                HexFormat.of().formatHex(Varints.packUnsigned(Long.MIN_VALUE)),
                HexFormat.of().formatHex(Varints.packUnsigned(-1L)),
                HexFormat.of().formatHex(Varints.packUnsigned(5L))
            ),
            "Bytes of totals of two to the sixty-third and above"
        );
    }
}
