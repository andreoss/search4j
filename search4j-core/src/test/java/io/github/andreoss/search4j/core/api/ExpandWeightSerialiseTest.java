/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the public API.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the serialised form of the expansion weights: the type byte and
 * the parameter k of the probabilistic weight survive a round trip.
 *
 * @since 0.5.4
 */
final class ExpandWeightSerialiseTest {

    @Test
    void carriesParameterThroughRoundTrip() {
        final List<String> found = new ArrayList<>(4);
        for (final double param : new double[] {0.5, 2.5, 0.0, 1.0}) {
            found.add(
                ExpandWeight.deserialise(new ProbeWeight(param).serialise()).toString()
            );
        }
        Assertions.assertEquals(
            List.of(
                "ProbeWeight(0.5)", "ProbeWeight(2.5)", "ProbeWeight(0.0)",
                "ProbeWeight(1.0)"
            ),
            found,
            "Parameter k after a round trip"
        );
    }

    @Test
    void weighsAlikeAfterRoundTrip() {
        final Enquire enquire = new EnquireImpl(WeightFixture.valued("main"));
        enquire.setQuery(MatcherVectors.parse("(OR alpha heavy)"));
        final ExpandWeight original = new ProbeWeight(2.5);
        Assertions.assertEquals(
            ExpandWeightSerialiseTest.text(enquire.getESet(10, original)),
            ExpandWeightSerialiseTest.text(
                enquire.getESet(10, ExpandWeight.deserialise(original.serialise()))
            ),
            "Terms and weights of a deserialised weight"
        );
    }

    @Test
    void writesParameterAsLittleEndianDouble() {
        Assertions.assertArrayEquals(
            new byte[] {2, 0, 0, 0, 0, 0, 0, 4, 64},
            new ProbeWeight(2.5).serialise(),
            "Type byte then k"
        );
    }

    @Test
    void writesBoseEinsteinWeightAsTypeOnly() {
        Assertions.assertEquals(
            List.of(1, "Bo1EWeight()"),
            List.of(
                new Bo1EWeight().serialise().length,
                ExpandWeight.deserialise(new Bo1EWeight().serialise()).toString()
            ),
            "Single type byte"
        );
    }

    @Test
    void copiesParameter() {
        Assertions.assertEquals(
            "ProbeWeight(0.5)", new ProbeWeight(0.5).copy().toString(),
            "Parameter k of a copy"
        );
    }

    @Test
    void refusesMalformedForms() {
        final List<byte[]> forms = List.of(
            new byte[0], new byte[] {9}, new byte[] {2, 0, 0, 0},
            new byte[] {1, 0}
        );
        for (final byte[] form : forms) {
            Assertions.assertThrows(
                SerialisationError.class, () -> ExpandWeight.deserialise(form),
                "Empty, unknown, truncated or overlong form"
            );
        }
    }

    private static String text(final ESet found) {
        final List<String> terms = new ArrayList<>(8);
        for (final ESet.Item item : found) {
            terms.add(String.format("%s:%s", item.term(), item.weight()));
        }
        return String.join(" ", terms);
    }
}
