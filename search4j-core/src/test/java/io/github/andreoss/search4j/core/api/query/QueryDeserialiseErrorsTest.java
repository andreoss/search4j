/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the query trees.
 */
package io.github.andreoss.search4j.core.api.query;

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.error.SerialisationError;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Error paths of reading a serialised query: the reference messages for
 * truncation, overflow and unknown type bytes, plus the checks of the port.
 *
 * @since 0.1.0
 */
final class QueryDeserialiseErrorsTest {

    /**
     * Reference message of a packed integer or string cut short.
     */
    private static final String SHORT = "Insufficient serialised data";

    /**
     * Reference message of a packed integer too large for its type.
     */
    private static final String OVERFLOW = "Serialised data overflowed type";

    /**
     * Reference message of a leaf cut short.
     */
    private static final String LEAF = "not enough data";

    /**
     * Message of the port for a byte left after the query.
     */
    private static final String TRAILING = "Extra data after serialised Query";

    /**
     * Message of the port for a value the Java types cannot hold.
     */
    private static final String UNSUPPORTED = "Unsupported serialised Query";

    @ParameterizedTest(name = "{0}")
    @MethodSource({"truncations", "unknowns", "unsupported"})
    void raisesSerialisationErrors(final String hex, final String message) {
        final byte[] data = HexFormat.of().parseHex(hex);
        Assertions.assertEquals(
            message,
            Assertions.assertThrows(
                SerialisationError.class, () -> Query.deserialise(data), hex
            ).getMessage(),
            hex
        );
    }

    @Test
    void rejectsANegativeFactorAsTheReference() {
        final byte[] data = HexFormat.of().parseHex("0d000000000000f0bf5161");
        Assertions.assertEquals(
            "SCALE_WEIGHT requires factor >= 0",
            Assertions.assertThrows(
                InvalidArgumentError.class, () -> Query.deserialise(data),
                "Negative factor"
            ).getMessage(),
            "Reference message of a negative factor"
        );
    }

    private static Stream<Arguments> truncations() {
        return Stream.of(
            Arguments.of("5568656c", "Not enough data"),
            Arguments.of("50", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("5080", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("6568656c6c6f", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("7568656c6c6f", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("7568656c6c6f02", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("6568656c6c6fffffffff7f", QueryDeserialiseErrorsTest.OVERFLOW),
            Arguments.of("7568656c6c6fffffffff1f00", QueryDeserialiseErrorsTest.OVERFLOW),
            Arguments.of("0d0000000000", "Bad encoded double: insufficient data"),
            Arguments.of("0e01", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0e", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0b", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0b0000", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0bffffffffff7f00000d", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0b00000d0561", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0a00000d", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0a00000d02", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0a00000d0200", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0a00", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("0c", QueryDeserialiseErrorsTest.LEAF),
            Arguments.of("21", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("2f", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("2fffffffff7f0000", QueryDeserialiseErrorsTest.OVERFLOW),
            Arguments.of("210161", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("21056162", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("33", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("88", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("6568656c6c6f808080808000", QueryDeserialiseErrorsTest.OVERFLOW),
            Arguments.of("888080808080808080808000", QueryDeserialiseErrorsTest.OVERFLOW),
            Arguments.of(
                "88ffffffffffffffffff02", QueryDeserialiseErrorsTest.OVERFLOW
            ),
            Arguments.of("ea", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("f8", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("f802", QueryDeserialiseErrorsTest.SHORT),
            Arguments.of("f2ffffffff7f5161", QueryDeserialiseErrorsTest.OVERFLOW)
        );
    }

    private static Stream<Arguments> unknowns() {
        return Stream.of(
            Arguments.of("01", "Unknown Query serialisation: 1"),
            Arguments.of("09", "Unknown Query serialisation: 9"),
            Arguments.of("10", "Unknown Query serialisation: 16"),
            Arguments.of("1f", "Unknown Query serialisation: 31"),
            Arguments.of("c2", "Unknown multi-way branch Query operator"),
            Arguments.of("e75161", "Unknown multi-way branch Query operator"),
            Arguments.of("0c03666f6f00", "PostingSource foo not registered"),
            Arguments.of("51615162", QueryDeserialiseErrorsTest.TRAILING),
            Arguments.of("00ff", QueryDeserialiseErrorsTest.TRAILING)
        );
    }

    private static Stream<Arguments> unsupported() {
        return Stream.of(
            Arguments.of("52c328", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("6161ffffffff0f", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0b00030d0161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0b00400d0161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0b00040d0161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0b0000050161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0a00000dffffffff0f000161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0a00000d02ffffffff0f0161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0a00000002000161", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of("0d0000000000000040", QueryDeserialiseErrorsTest.UNSUPPORTED),
            Arguments.of(
                "88ffffffffffffffffff01", QueryDeserialiseErrorsTest.UNSUPPORTED
            )
        );
    }
}
