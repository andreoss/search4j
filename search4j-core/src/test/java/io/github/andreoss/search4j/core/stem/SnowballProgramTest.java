/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the Snowball stemming runtime.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.error.EngineInternalError;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link SnowballProgram} against the runtime contract.
 *
 * @since 0.1.0
 */
@SuppressWarnings({
    "PMD.UnitTestContainsTooManyAsserts",
    "PMD.UnnecessaryLocalRule",
    "PMD.UncommentedEmptyMethodBody"
})
final class SnowballProgramTest {

    /**
     * Group holding a, e, i, o and u between a and z.
     */
    private static final int[] VOWELS = {0x104111};

    @Test
    void matchesAForwardSlice() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("caresses");
        Assertions.assertTrue(probe.forward("caress"), "Prefix matched");
        Assertions.assertEquals(6, probe.position(), "Cursor after the match");
    }

    @Test
    void rejectsAForwardSliceBeyondTheLimit() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("cat");
        Assertions.assertFalse(probe.forward("cats"), "Slice too long");
        Assertions.assertEquals(0, probe.position(), "Cursor unchanged");
    }

    @Test
    void matchesABackwardSlice() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("caresses");
        probe.end();
        Assertions.assertTrue(probe.backward("sses"), "Suffix matched");
        Assertions.assertEquals(4, probe.position(), "Cursor before the suffix");
    }

    @Test
    void rejectsABackwardSliceBeyondTheLimit() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("cat");
        probe.end();
        Assertions.assertFalse(probe.backward("scat"), "Slice too long");
        Assertions.assertEquals(3, probe.position(), "Cursor unchanged");
    }

    @Test
    void acceptsAGroupedCharacter() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("ab");
        Assertions.assertTrue(
            probe.grouping(SnowballProgramTest.VOWELS, 97, 122),
            "Letter inside the group"
        );
    }

    @Test
    void rejectsACharacterOutsideTheGroup() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("zb");
        Assertions.assertFalse(
            probe.grouping(SnowballProgramTest.VOWELS, 97, 122),
            "Letter outside the group"
        );
    }

    @Test
    void acceptsAnOutGroupedCharacter() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("zb");
        Assertions.assertTrue(
            probe.outside(SnowballProgramTest.VOWELS, 97, 122),
            "Letter outside the group"
        );
    }

    @Test
    void acceptsACharacterInsideARange() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("cat");
        Assertions.assertTrue(probe.range(97, 122), "Letter inside the range");
    }

    @Test
    void rejectsACharacterOutsideARange() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("Cat");
        Assertions.assertFalse(probe.range(97, 122), "Letter outside the range");
    }

    @Test
    void picksTheLongestAmongEntry() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("ational");
        final Among[] table = {
            new Among("al", -1, 1), new Among("ational", -1, 2),
        };
        Assertions.assertEquals(2, probe.among(table), "Longest entry");
        Assertions.assertEquals(7, probe.position(), "Cursor after the entry");
    }

    @Test
    void reportsNoAmongEntryWhenNoneMatches() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("ational");
        final Among[] table = {new Among("ness", -1, 1)};
        Assertions.assertEquals(0, probe.among(table), "No entry matched");
        Assertions.assertEquals(0, probe.position(), "Cursor unchanged");
    }

    @Test
    void picksTheLongestBackwardAmongEntry() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("relational");
        probe.end();
        final Among[] table = {
            new Among("al", -1, 1), new Among("ional", -1, 2),
        };
        Assertions.assertEquals(2, probe.amongBack(table), "Longest entry");
        Assertions.assertEquals(5, probe.start(), "Start of the match");
    }

    @Test
    void replacesTheSlice() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("caresses");
        probe.mark(4, 8);
        probe.slice("ing");
        Assertions.assertEquals("careing", probe.text(), "Replaced slice");
    }

    @Test
    void deletesTheSlice() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("caresses");
        probe.mark(4, 8);
        probe.remove();
        Assertions.assertEquals("care", probe.text(), "Deleted slice");
    }

    @Test
    void rejectsAnInvalidSlice() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("cat");
        probe.mark(2, 1);
        Assertions.assertThrows(
            EngineInternalError.class, probe::remove, "Slice out of order"
        );
    }

    @Test
    void movesTheCursorOneCharacterAtATime() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("ab");
        Assertions.assertTrue(probe.stepForward(), "Moved forward");
        Assertions.assertTrue(probe.stepForward(), "Moved forward");
        Assertions.assertFalse(probe.stepForward(), "At the end");
    }

    @Test
    void copiesTheWholeState() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        probe.load("cat");
        probe.end();
        final SnowballProgramTest.Probe copy = new SnowballProgramTest.Probe();
        copy.copy(probe);
        Assertions.assertEquals("cat", copy.text(), "Copied buffer");
        Assertions.assertEquals(3, copy.position(), "Copied cursor");
    }

    @Test
    void reportsAnEmptyStemForAnEmptyProgram() {
        final SnowballProgramTest.Probe probe = new SnowballProgramTest.Probe();
        Assertions.assertEquals("", probe.text(), "Empty buffer");
    }

    /**
     * Runtime probe exposing the primitives of the base class.
     *
     * @since 0.1.0
     */
    private static final class Probe extends SnowballProgram {

        @Override
        public void stem() {
        }

        void load(final String text) {
            this.current(text);
        }

        void copy(final SnowballProgram other) {
            this.copyFrom(other);
        }

        String text() {
            return this.current();
        }

        int position() {
            return this.cursor();
        }

        int start() {
            return this.bra();
        }

        void end() {
            this.cursor(this.limit());
        }

        void mark(final int start, final int end) {
            this.bra(start);
            this.ket(end);
        }

        boolean forward(final String part) {
            return this.eqS(part);
        }

        boolean backward(final String part) {
            return this.eqSB(part);
        }

        boolean grouping(final int[] groups, final int min, final int max) {
            return this.inGrouping(groups, min, max);
        }

        boolean outside(final int[] groups, final int min, final int max) {
            return this.outGrouping(groups, min, max);
        }

        boolean range(final int min, final int max) {
            return this.inRange(min, max);
        }

        int among(final Among... table) {
            return this.findAmong(table);
        }

        int amongBack(final Among... table) {
            return this.findAmongB(table);
        }

        void slice(final String text) {
            this.sliceFrom(text);
        }

        void remove() {
            this.sliceDel();
        }

        boolean stepForward() {
            return this.nextChar();
        }
    }
}
