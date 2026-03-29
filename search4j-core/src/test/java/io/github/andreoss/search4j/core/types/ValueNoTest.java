/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the value slot number value type.
 */
package io.github.andreoss.search4j.core.types;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ValueNo}.
 *
 * @since 0.1.0
 */
final class ValueNoTest {

    @Test
    void badSlotIsAllBitsSet() {
        Assertions.assertEquals(
            0xFFFFFFFFL, ValueNo.BAD, "The bad slot is all bits set"
        );
    }

    @Test
    void badSlotRendersUnsigned() {
        Assertions.assertEquals(
            "4294967295", new ValueNo(ValueNo.BAD).toString(),
            "The bad slot renders as its unsigned decimal value"
        );
    }

    @Test
    void rejectsNegativeSlot() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new ValueNo(-1L),
            "Negative slot numbers are invalid"
        );
    }

    @Test
    void rejectsPastUnsignedRange() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new ValueNo(0x100000000L),
            "One past the unsigned 32-bit range must be rejected"
        );
    }

    @Test
    void parsesDecimalSlot() {
        Assertions.assertEquals(
            9, ValueNo.parse("9").value(), "Decimal digits must parse"
        );
    }

    @Test
    void parsesBadSlot() {
        Assertions.assertEquals(
            ValueNo.BAD, ValueNo.parse("4294967295").value(),
            "The bad slot must parse"
        );
    }

    @Test
    void rejectsPastUnsignedDecimal() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ValueNo.parse("4294967296"),
            "One past the unsigned 32-bit range must be rejected"
        );
    }

    @Test
    void rejectsNullText() {
        Assertions.assertThrows(
            NullPointerException.class,
            () -> ValueNo.parse(null),
            "Null text must be rejected"
        );
    }

    @Test
    void badSlotIsAbsent() {
        Assertions.assertTrue(
            new ValueNo(ValueNo.BAD).absent(), "The bad slot is absent"
        );
    }

    @Test
    void zeroSlotIsPresent() {
        Assertions.assertFalse(
            new ValueNo(0L).absent(), "Slot zero is a real slot"
        );
    }

    @Test
    void ordersNumerically() {
        Assertions.assertTrue(
            new ValueNo(2L).compareTo(new ValueNo(10L)) < 0,
            "2 must sort before 10"
        );
    }
}
