/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the document identifier value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link DocId}.
 *
 * @since 0.1.0
 */
final class DocIdTest {

    @Test
    void acceptsPositiveNumber() {
        Assertions.assertEquals(
            7, DocId.of(7).value(),
            "A positive number must be kept"
        );
    }

    @Test
    void rejectsZeroAndNegative() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.of(0),
            "Zero is not a valid document"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.of(-1),
            "Negative identifiers are invalid"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new DocId(-3),
            "The canonical constructor must reject negatives"
        );
    }

    @Test
    void noneIsNotValid() {
        Assertions.assertFalse(
            DocId.NONE.valid(), "Identifier 0 is not a valid document"
        );
    }

    @Test
    void positiveIsValid() {
        Assertions.assertTrue(
            DocId.of(1).valid(), "Identifier 1 is a valid document"
        );
    }

    @Test
    void canonicalZeroIsNotValid() {
        Assertions.assertFalse(
            new DocId(0).valid(),
            "Canonical constructor allows 0 as the absent marker"
        );
    }

    @Test
    void parsesPositiveDigits() {
        Assertions.assertEquals(
            42, DocId.parse("42").value(), "Decimal digits must parse"
        );
    }

    @Test
    void parsesZero() {
        Assertions.assertEquals(
            0, DocId.parse("0").value(), "Zero must parse as the marker"
        );
    }

    @Test
    void parsesLargestValue() {
        Assertions.assertEquals(
            Integer.MAX_VALUE,
            DocId.parse("2147483647").value(),
            "The largest 32-bit number must parse"
        );
    }

    @Test
    void rejectsMalformedText() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.parse("2147483648"),
            "One past the signed 32-bit range must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.parse("-5"),
            "A negative sign must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.parse("007"),
            "Leading zeros must be rejected"
        );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> DocId.parse("4x"),
            "Non-digits must be rejected"
        );
        Assertions.assertThrows(
            NullPointerException.class,
            () -> DocId.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void ordersBefore() {
        Assertions.assertTrue(
            DocId.of(2).compareTo(DocId.of(10)) < 0, "2 sorts before 10"
        );
    }

    @Test
    void equalIdentifiersOrderEqual() {
        Assertions.assertEquals(
            0, DocId.of(5).compareTo(DocId.of(5)), "Equal identifiers"
        );
    }

    @Test
    void nextAdvancesByOne() {
        Assertions.assertEquals(
            6, DocId.of(5).next().value(), "Next advances by one"
        );
    }

    @Test
    void nextDoesNotOverflow() {
        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new DocId(Integer.MAX_VALUE).next(),
            "Next must not overflow"
        );
    }

    @Test
    void rendersDecimal() {
        Assertions.assertEquals(
            "12", DocId.of(12).toString(), "Text must be decimal"
        );
    }
}
