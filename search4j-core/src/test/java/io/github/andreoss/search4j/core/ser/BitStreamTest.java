/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the serialisation utilities.
 */
package io.github.andreoss.search4j.core.ser;

import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link BitWriter} and {@link BitReader}: the codes of the
 * reference, interpolative vectors by hand and random round trips.
 *
 * @since 0.1.0
 */
final class BitStreamTest {

    /**
     * Ascending values of the many-position vector.
     */
    private static final long[] MANY = {1L, 2L, 3L, 5L, 8L, 13L, 21L, 34L};

    /**
     * Bytes of the many-position vector after its bounds.
     */
    private static final String MANY_HEX = "8109181d";

    @Test
    void encodesCodesOfElevenAsReference() {
        final List<String> seen = new ArrayList<>(11);
        for (long value = 0L; value < 11L; ++value) {
            final BitWriter writer = new BitWriter();
            writer.encode(value, 11L);
            writer.encode(1L, 2L);
            seen.add(HexFormat.of().formatHex(writer.freeze()));
        }
        Assertions.assertEquals(
            List.of(
                "10", "11", "12", "0b", "0c", "0d", "0e", "0f", "18", "19",
                "1a"
            ),
            seen,
            "Four bits, three for the middle band, a set bit after each"
        );
    }

    @Test
    void decodesCodesOfEleven() {
        final List<Long> seen = new ArrayList<>(11);
        final BitWriter writer = new BitWriter();
        for (long value = 10L; value >= 0L; --value) {
            writer.encode(value, 11L);
        }
        final BitReader reader = new BitReader(writer.freeze());
        for (int idx = 0; idx < 11; ++idx) {
            seen.add(reader.decode(11L));
        }
        Assertions.assertEquals(
            List.of(10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L, 2L, 1L, 0L), seen,
            "Every code of eleven decoded in turn"
        );
    }

    @Test
    void packsLeastSignificantBitFirst() {
        final BitWriter writer = new BitWriter();
        writer.encode(9L, 11L);
        writer.encode(3L, 11L);
        writer.encode(0L, 1L);
        writer.encode(5L, 8L);
        Assertions.assertEquals(
            "b902", HexFormat.of().formatHex(writer.freeze()),
            "Nine in four bits, three in three, nothing, five in three"
        );
    }

    @Test
    void encodesManyPositionsAsReference() {
        final BitWriter writer = new BitWriter();
        writer.encode(1L, 34L);
        writer.encode(6L, 33L);
        writer.encodeInterpolative(BitStreamTest.MANY, 0, 7);
        Assertions.assertEquals(
            BitStreamTest.MANY_HEX, HexFormat.of().formatHex(writer.freeze()),
            "First, count and the interpolative bits by hand"
        );
    }

    @Test
    void decodesManyPositionsOnDemand() {
        final List<Object> seen = new ArrayList<>(12);
        final BitReader reader = new BitReader(
            HexFormat.of().parseHex(BitStreamTest.MANY_HEX)
        );
        seen.add(reader.decode(34L));
        seen.add(reader.decode(33L));
        seen.add(reader.allGone());
        reader.decodeInterpolative(7, 1L, 34L);
        for (int idx = 1; idx < 8; ++idx) {
            seen.add(reader.decodeInterpolativeNext());
        }
        seen.add(reader.allGone());
        seen.add(reader.decodeInterpolativeNext());
        Assertions.assertEquals(
            List.of(
                1L, 6L, false, 2L, 3L, 5L, 8L, 13L, 21L, 34L, true, 34L
            ),
            seen,
            "First, count, then every position, the last one repeated"
        );
    }

    @Test
    void roundTripsRandomSequences() {
        final Random random = new Random(17L);
        final List<String> faults = new ArrayList<>(0);
        for (int round = 0; round < 200; ++round) {
            final long[] values = BitStreamTest.ascending(random);
            final BitWriter writer = new BitWriter();
            writer.encodeInterpolative(values, 0, values.length - 1);
            final BitReader reader = new BitReader(writer.freeze());
            reader.decodeInterpolative(
                values.length - 1, values[0], values[values.length - 1]
            );
            for (int idx = 1; idx < values.length; ++idx) {
                final long value = reader.decodeInterpolativeNext();
                if (value != values[idx]) {
                    faults.add(
                        String.format("%d/%d: %d", round, idx, value)
                    );
                }
            }
            if (!reader.allGone()) {
                faults.add(String.format("%d: bits left", round));
            }
        }
        Assertions.assertEquals(
            List.of(), faults, "Random ascending sequences read back"
        );
    }

    @Test
    void decodesPairWithoutMiddle() {
        final BitReader reader = new BitReader(new byte[0]);
        reader.decodeInterpolative(1, 4L, 9L);
        Assertions.assertEquals(
            List.of(9L, 9L),
            List.of(
                reader.decodeInterpolativeNext(),
                reader.decodeInterpolativeNext()
            ),
            "Upper bound at once, nothing read"
        );
    }

    @Test
    void refusesMisuse() {
        final List<Object> seen = new ArrayList<>(7);
        final BitWriter writer = new BitWriter();
        seen.add(BitStreamTest.failure(() -> writer.encode(3L, 3L)));
        seen.add(BitStreamTest.failure(() -> writer.encode(-1L, 3L)));
        seen.add(BitStreamTest.failure(() -> writer.encode(0L, 0L)));
        seen.add(BitStreamTest.failure(() -> writer.encode(0L, 1L << 33)));
        final BitReader reader = new BitReader(new byte[] {0x7F});
        seen.add(BitStreamTest.failure(reader::decodeInterpolativeNext));
        reader.decodeInterpolative(2, 1L, 1_000_000L);
        seen.add(
            BitStreamTest.failure(() -> reader.decodeInterpolative(2, 1L, 9L))
        );
        seen.add(BitStreamTest.failure(reader::decodeInterpolativeNext));
        seen.add(reader.allGone());
        Assertions.assertEquals(
            List.of(
                IllegalArgumentException.class, IllegalArgumentException.class,
                IllegalArgumentException.class, IllegalArgumentException.class,
                IllegalStateException.class, IllegalStateException.class,
                SerialisationError.class, false
            ),
            seen,
            "Bad values and bounds, sequence state, stream exhausted"
        );
    }

    private static long[] ascending(final Random random) {
        final int size = 2 + random.nextInt(60);
        final long[] values = new long[size];
        long value = random.nextInt(100);
        for (int idx = 0; idx < size; ++idx) {
            value = value + 1L + random.nextInt(1 << random.nextInt(24));
            values[idx] = value;
        }
        return values;
    }

    private static Class<?> failure(final Runnable action) {
        Class<?> result = Void.class;
        try {
            action.run();
        } catch (final IllegalArgumentException | IllegalStateException
            | SerialisationError ex) {
            result = ex.getClass();
        }
        return result;
    }
}
