/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the Snowball stemming runtime.
 */
package io.github.andreoss.search4j.core.stem;

import io.github.andreoss.search4j.core.error.EngineInternalError;
import java.nio.CharBuffer;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Matching primitives of {@link SnowballProgram} against the runtime
 * contract: a forward primitive tests the character at the cursor below
 * the limit and steps over it, a backward primitive tests the character
 * before the cursor above the lower bound and steps back over it, and a
 * failed test leaves the cursor where it was.
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.UnitTestContainsTooManyAsserts")
final class SnowballPrimitivesTest {

    /**
     * Group from A to z holding B, a and z, spread over two words.
     */
    private static final int[] WIDE = {0x2, 0x2000001};

    @ParameterizedTest(name = "{0}")
    @MethodSource({"ranges", "groupings", "strings", "moves"})
    void stepsLikeTheRuntime(
        final String name, final SnowballPrimitivesTest.Case kase
    ) {
        final SnowballPrimitivesTest.Probe probe = kase.setup().probe();
        Assertions.assertEquals(kase.result(), kase.step().apply(probe), name);
        Assertions.assertEquals(kase.cursor(), probe.position(), name);
    }

    @Test
    void picksTheLongestEntryWithinTheLimit() {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("abcd", 0, 0, 2).probe();
        Assertions.assertEquals(
            2,
            probe.among(
                new Among("a", -1, 1), new Among("abc", -1, 3),
                new Among("ab", -1, 2), new Among("b", -1, 4)
            ),
            "longest entry inside the limit"
        );
        Assertions.assertEquals(2, probe.position(), "cursor after the entry");
    }

    @Test
    void picksTheLongestBackwardEntryAboveTheBound() {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("xab", 3, 1, 3).probe();
        Assertions.assertEquals(
            2,
            probe.amongBack(
                new Among("b", -1, 1), new Among("xab", -1, 3),
                new Among("ab", -1, 2), new Among("cb", -1, 4)
            ),
            "longest entry above the bound"
        );
        Assertions.assertEquals(1, probe.position(), "cursor before the entry");
    }

    @Test
    void movesTheCursorAfterAGrowingReplacement() {
        Assertions.assertEquals(
            List.of(
                List.of(1, 0, 7, "aXYZdef"),
                List.of(1, 1, 7, "aXYZdef"),
                List.of(1, 1, 7, "aXYZdef"),
                List.of(1, 4, 7, "aXYZdef"),
                List.of(1, 6, 7, "aXYZdef")
            ),
            Stream.of(0, 1, 2, 3, 5)
                .map(at -> SnowballPrimitivesTest.replaced(at, "XYZ"))
                .toList(),
            "cursor before, inside and after the replaced span"
        );
    }

    @Test
    void movesTheCursorAfterAShrinkingReplacement() {
        Assertions.assertEquals(
            List.of(
                List.of(-1, 0, 5, "aXdef"),
                List.of(-1, 1, 5, "aXdef"),
                List.of(-1, 2, 5, "aXdef"),
                List.of(-1, 4, 5, "aXdef")
            ),
            Stream.of(0, 2, 3, 5)
                .map(at -> SnowballPrimitivesTest.replaced(at, "X"))
                .toList(),
            "cursor before, inside and after the replaced span"
        );
    }

    @Test
    void keepsTheCursorAfterAnEqualReplacement() {
        Assertions.assertEquals(
            List.of(0, 2, 6, "aXYdef"),
            SnowballPrimitivesTest.replaced(2, "XY"),
            "nothing moves when the length stays"
        );
    }

    @Test
    void acceptsASliceEndingAtTheLimit() {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("abc", 0, 0, 3).probe();
        probe.mark(3, 3);
        probe.slice("d");
        Assertions.assertEquals("abcd", probe.text(), "appended");
    }

    @Test
    void rejectsInvalidSlices() {
        Assertions.assertEquals(
            List.of(true, true, true, false),
            Stream.of(
                new int[] {-1, 1}, new int[] {2, 1}, new int[] {0, 4},
                new int[] {0, 0}
            ).map(SnowballPrimitivesTest::invalid).toList(),
            "negative start, reversed and beyond the limit fail"
        );
    }

    @Test
    void copiesEveryMark() {
        final SnowballPrimitivesTest.Probe source =
            new SnowballPrimitivesTest.Setup("abcdef", 4, 2, 5).probe();
        source.mark(1, 3);
        final SnowballPrimitivesTest.Probe copy =
            new SnowballPrimitivesTest.Probe();
        copy.copy(source);
        Assertions.assertEquals(
            List.of("abcdef", 4, 5, 1, 3, 2),
            List.of(
                copy.text(), copy.position(), copy.end(), copy.start(),
                copy.stop(), copy.lower()
            ),
            "buffer, cursor, limit, slice and lower bound"
        );
    }

    @Test
    void resetsEveryMarkOnLoad() {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("abcdef", 4, 2, 5).probe();
        probe.mark(1, 3);
        probe.load("xy");
        Assertions.assertEquals(
            List.of("xy", 0, 2, 0, 0, 0),
            List.of(
                probe.text(), probe.position(), probe.end(), probe.start(),
                probe.stop(), probe.lower()
            ),
            "fresh buffer and marks"
        );
    }

    private static List<Object> replaced(final int cursor, final String text) {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("abcdef", cursor, 0, 6).probe();
        return List.of(
            probe.replace(1, 3, text), probe.position(), probe.end(), probe.text()
        );
    }

    private static boolean invalid(final int... slice) {
        final SnowballPrimitivesTest.Probe probe =
            new SnowballPrimitivesTest.Setup("abc", 0, 0, 3).probe();
        probe.mark(slice[0], slice[1]);
        boolean failed = false;
        try {
            probe.slice("");
        } catch (final EngineInternalError ex) {
            failed = true;
        }
        return failed;
    }

    private static Stream<Arguments> ranges() {
        final String word = "abcde";
        return Stream.of(
            SnowballPrimitivesTest.forward(word, 0, p -> p.inside(98, 100), false, 0),
            SnowballPrimitivesTest.forward(word, 1, p -> p.inside(98, 100), true, 2),
            SnowballPrimitivesTest.forward(word, 3, p -> p.inside(98, 100), true, 4),
            SnowballPrimitivesTest.forward(word, 4, p -> p.inside(98, 100), false, 4),
            SnowballPrimitivesTest.forward(word, 5, p -> p.inside(97, 101), false, 5),
            SnowballPrimitivesTest.backward(word, 1, p -> p.insideBack(98, 100), false, 1),
            SnowballPrimitivesTest.backward(word, 2, p -> p.insideBack(98, 100), true, 1),
            SnowballPrimitivesTest.backward(word, 4, p -> p.insideBack(98, 100), true, 3),
            SnowballPrimitivesTest.backward(word, 5, p -> p.insideBack(98, 100), false, 5),
            SnowballPrimitivesTest.backward(word, 0, p -> p.insideBack(97, 101), false, 0),
            SnowballPrimitivesTest.forward(word, 0, p -> p.outsideRange(98, 100), true, 1),
            SnowballPrimitivesTest.forward(word, 1, p -> p.outsideRange(98, 100), false, 1),
            SnowballPrimitivesTest.forward(word, 3, p -> p.outsideRange(98, 100), false, 3),
            SnowballPrimitivesTest.forward(word, 4, p -> p.outsideRange(98, 100), true, 5),
            SnowballPrimitivesTest.forward(word, 5, p -> p.outsideRange(98, 100), false, 5),
            SnowballPrimitivesTest.backward(word, 1, p -> p.outsideRangeBack(98, 100), true, 0),
            SnowballPrimitivesTest.backward(word, 2, p -> p.outsideRangeBack(98, 100), false, 2),
            SnowballPrimitivesTest.backward(word, 4, p -> p.outsideRangeBack(98, 100), false, 4),
            SnowballPrimitivesTest.backward(word, 5, p -> p.outsideRangeBack(98, 100), true, 4),
            SnowballPrimitivesTest.backward(word, 0, p -> p.outsideRangeBack(98, 100), false, 0),
            SnowballPrimitivesTest.kase(
                "range at the limit",
                new SnowballPrimitivesTest.Setup(word, 1, 0, 1),
                SnowballPrimitivesTest.call(p -> p.inside(97, 101)), false, 1
            ),
            SnowballPrimitivesTest.kase(
                "outside range at the limit",
                new SnowballPrimitivesTest.Setup(word, 0, 0, 0),
                SnowballPrimitivesTest.call(p -> p.outsideRange(98, 100)), false, 0
            ),
            SnowballPrimitivesTest.kase(
                "backward range at the bound",
                new SnowballPrimitivesTest.Setup(word, 2, 2, 5),
                SnowballPrimitivesTest.call(p -> p.insideBack(97, 101)), false, 2
            ),
            SnowballPrimitivesTest.kase(
                "backward outside range at the bound",
                new SnowballPrimitivesTest.Setup(word, 1, 1, 5),
                SnowballPrimitivesTest.call(p -> p.outsideRangeBack(98, 100)),
                false, 1
            )
        );
    }

    private static Stream<Arguments> groupings() {
        final String word = "@ABaz{";
        final int[] wide = SnowballPrimitivesTest.WIDE;
        final int low = 65;
        final int high = 122;
        return Stream.of(
            SnowballPrimitivesTest.forward(word, 0, p -> p.grouped(wide, low, high), false, 0),
            SnowballPrimitivesTest.forward(word, 1, p -> p.grouped(wide, low, high), false, 1),
            SnowballPrimitivesTest.forward(word, 2, p -> p.grouped(wide, low, high), true, 3),
            SnowballPrimitivesTest.forward(word, 3, p -> p.grouped(wide, low, high), true, 4),
            SnowballPrimitivesTest.forward(word, 4, p -> p.grouped(wide, low, high), true, 5),
            SnowballPrimitivesTest.forward(word, 5, p -> p.grouped(wide, low, high), false, 5),
            SnowballPrimitivesTest.forward(word, 6, p -> p.grouped(wide, low, high), false, 6),
            SnowballPrimitivesTest.backward(word, 1, p -> p.groupedBack(wide, low, high), false, 1),
            SnowballPrimitivesTest.backward(word, 2, p -> p.groupedBack(wide, low, high), false, 2),
            SnowballPrimitivesTest.backward(word, 3, p -> p.groupedBack(wide, low, high), true, 2),
            SnowballPrimitivesTest.backward(word, 4, p -> p.groupedBack(wide, low, high), true, 3),
            SnowballPrimitivesTest.backward(word, 5, p -> p.groupedBack(wide, low, high), true, 4),
            SnowballPrimitivesTest.backward(word, 6, p -> p.groupedBack(wide, low, high), false, 6),
            SnowballPrimitivesTest.backward(word, 0, p -> p.groupedBack(wide, low, high), false, 0),
            SnowballPrimitivesTest.forward(word, 0, p -> p.ungrouped(wide, low, high), true, 1),
            SnowballPrimitivesTest.forward(word, 1, p -> p.ungrouped(wide, low, high), true, 2),
            SnowballPrimitivesTest.forward(word, 2, p -> p.ungrouped(wide, low, high), false, 2),
            SnowballPrimitivesTest.forward(word, 3, p -> p.ungrouped(wide, low, high), false, 3),
            SnowballPrimitivesTest.forward(word, 5, p -> p.ungrouped(wide, low, high), true, 6),
            SnowballPrimitivesTest.forward(word, 6, p -> p.ungrouped(wide, low, high), false, 6),
            SnowballPrimitivesTest.backward(word, 1, p -> p.outBack(wide, low, high), true, 0),
            SnowballPrimitivesTest.backward(word, 2, p -> p.outBack(wide, low, high), true, 1),
            SnowballPrimitivesTest.backward(word, 3, p -> p.outBack(wide, low, high), false, 3),
            SnowballPrimitivesTest.backward(word, 5, p -> p.outBack(wide, low, high), false, 5),
            SnowballPrimitivesTest.backward(word, 6, p -> p.outBack(wide, low, high), true, 5),
            SnowballPrimitivesTest.backward(word, 0, p -> p.outBack(wide, low, high), false, 0),
            SnowballPrimitivesTest.kase(
                "grouping at the limit",
                new SnowballPrimitivesTest.Setup(word, 2, 0, 2),
                SnowballPrimitivesTest.call(p -> p.grouped(wide, low, high)), false, 2
            ),
            SnowballPrimitivesTest.kase(
                "outside grouping at the limit",
                new SnowballPrimitivesTest.Setup(word, 0, 0, 0),
                SnowballPrimitivesTest.call(p -> p.ungrouped(wide, low, high)), false, 0
            ),
            SnowballPrimitivesTest.kase(
                "backward grouping at the bound",
                new SnowballPrimitivesTest.Setup(word, 3, 3, 6),
                SnowballPrimitivesTest.call(p -> p.groupedBack(wide, low, high)),
                false, 3
            ),
            SnowballPrimitivesTest.kase(
                "backward outside grouping at the bound",
                new SnowballPrimitivesTest.Setup(word, 1, 1, 6),
                SnowballPrimitivesTest.call(p -> p.outBack(wide, low, high)),
                false, 1
            )
        );
    }

    private static Stream<Arguments> strings() {
        final String word = "abcab";
        return Stream.of(
            SnowballPrimitivesTest.forward(word, 0, p -> p.equal("abc"), true, 3),
            SnowballPrimitivesTest.forward(word, 0, p -> p.equal("abd"), false, 0),
            SnowballPrimitivesTest.forward(word, 3, p -> p.equal("ab"), true, 5),
            SnowballPrimitivesTest.forward(word, 3, p -> p.equal("abc"), false, 3),
            SnowballPrimitivesTest.forward(word, 0, p -> p.equalV("abc"), true, 3),
            SnowballPrimitivesTest.forward(word, 0, p -> p.equalV("abd"), false, 0),
            SnowballPrimitivesTest.forward(word, 0, p -> p.equalV("xbc"), false, 0),
            SnowballPrimitivesTest.forward(word, 3, p -> p.equalV("ab"), true, 5),
            SnowballPrimitivesTest.forward(word, 3, p -> p.equalV("abc"), false, 3),
            SnowballPrimitivesTest.forward(word, 2, p -> p.equalV(""), true, 2),
            SnowballPrimitivesTest.backward(word, 5, p -> p.equalBack("cab"), true, 2),
            SnowballPrimitivesTest.backward(word, 5, p -> p.equalBack("xab"), false, 5),
            SnowballPrimitivesTest.backward(word, 2, p -> p.equalBack("ab"), true, 0),
            SnowballPrimitivesTest.backward(word, 2, p -> p.equalBack("cab"), false, 2),
            SnowballPrimitivesTest.backward(word, 5, p -> p.equalVBack("cab"), true, 2),
            SnowballPrimitivesTest.backward(word, 5, p -> p.equalVBack("xab"), false, 5),
            SnowballPrimitivesTest.backward(word, 5, p -> p.equalVBack("cax"), false, 5),
            SnowballPrimitivesTest.backward(word, 2, p -> p.equalVBack("ab"), true, 0),
            SnowballPrimitivesTest.backward(word, 2, p -> p.equalVBack("cab"), false, 2),
            SnowballPrimitivesTest.backward(word, 3, p -> p.equalVBack(""), true, 3),
            SnowballPrimitivesTest.kase(
                "forward string beyond the limit",
                new SnowballPrimitivesTest.Setup(word, 0, 0, 2),
                SnowballPrimitivesTest.call(p -> p.equalV("abc")), false, 0
            ),
            SnowballPrimitivesTest.kase(
                "forward string ending at the limit",
                new SnowballPrimitivesTest.Setup(word, 0, 0, 3),
                SnowballPrimitivesTest.call(p -> p.equalV("abc")), true, 3
            ),
            SnowballPrimitivesTest.kase(
                "backward string below the bound",
                new SnowballPrimitivesTest.Setup(word, 5, 3, 5),
                SnowballPrimitivesTest.call(p -> p.equalVBack("cab")), false, 5
            ),
            SnowballPrimitivesTest.kase(
                "backward string starting at the bound",
                new SnowballPrimitivesTest.Setup(word, 5, 2, 5),
                SnowballPrimitivesTest.call(p -> p.equalVBack("cab")), true, 2
            ),
            SnowballPrimitivesTest.kase(
                "plain backward string below the bound",
                new SnowballPrimitivesTest.Setup(word, 5, 3, 5),
                SnowballPrimitivesTest.call(p -> p.equalBack("cab")), false, 5
            ),
            SnowballPrimitivesTest.kase(
                "plain forward string beyond the limit",
                new SnowballPrimitivesTest.Setup(word, 0, 0, 2),
                SnowballPrimitivesTest.call(p -> p.equal("abc")), false, 0
            )
        );
    }

    private static Stream<Arguments> moves() {
        return Stream.of(
            SnowballPrimitivesTest.backward("abc", 2, SnowballPrimitivesTest.Probe::back, true, 1),
            SnowballPrimitivesTest.backward("abc", 0, SnowballPrimitivesTest.Probe::back, false, 0),
            SnowballPrimitivesTest.kase(
                "step back at the bound",
                new SnowballPrimitivesTest.Setup("abc", 1, 1, 3),
                SnowballPrimitivesTest.call(SnowballPrimitivesTest.Probe::back), false, 1
            ),
            SnowballPrimitivesTest.kase(
                "step forward at the limit",
                new SnowballPrimitivesTest.Setup("abc", 2, 0, 2),
                SnowballPrimitivesTest.call(SnowballPrimitivesTest.Probe::ahead), false, 2
            )
        );
    }

    private static Arguments forward(
        final String word, final int cursor,
        final Function<SnowballPrimitivesTest.Probe, Object> step,
        final Object result, final int after
    ) {
        return SnowballPrimitivesTest.kase(
            String.format("forward %s at %d", word, cursor),
            new SnowballPrimitivesTest.Setup(word, cursor, 0, word.length()),
            step, result, after
        );
    }

    private static Arguments backward(
        final String word, final int cursor,
        final Function<SnowballPrimitivesTest.Probe, Object> step,
        final Object result, final int after
    ) {
        return SnowballPrimitivesTest.kase(
            String.format("backward %s at %d", word, cursor),
            new SnowballPrimitivesTest.Setup(word, cursor, 0, word.length()),
            step, result, after
        );
    }

    private static Arguments kase(
        final String name, final SnowballPrimitivesTest.Setup setup,
        final Function<SnowballPrimitivesTest.Probe, Object> step,
        final Object result, final int cursor
    ) {
        return Arguments.of(
            name, new SnowballPrimitivesTest.Case(setup, step, result, cursor)
        );
    }

    private static Function<SnowballPrimitivesTest.Probe, Object> call(
        final Function<SnowballPrimitivesTest.Probe, Object> step
    ) {
        return step;
    }

    /**
     * One step with its expected outcome.
     *
     * @param setup Buffer and marks before the step
     * @param step The primitive applied
     * @param result Expected result
     * @param cursor Expected cursor after the step
     * @since 0.1.0
     */
    record Case(
        SnowballPrimitivesTest.Setup setup,
        Function<SnowballPrimitivesTest.Probe, Object> step,
        Object result, int cursor
    ) {
    }

    /**
     * A buffer with its cursor and bounds.
     *
     * @param word Buffer
     * @param cursor Cursor
     * @param lower Lower bound of backward scans
     * @param upper Limit of forward scans
     * @since 0.1.0
     */
    record Setup(String word, int cursor, int lower, int upper) {

        /**
         * A probe loaded with the setup.
         *
         * @return Probe
         */
        SnowballPrimitivesTest.Probe probe() {
            final SnowballPrimitivesTest.Probe probe =
                new SnowballPrimitivesTest.Probe();
            probe.load(this.word);
            probe.place(this.cursor, this.lower, this.upper);
            return probe;
        }
    }

    /**
     * Runtime probe exposing the primitives of the base class.
     *
     * @since 0.1.0
     */
    static final class Probe extends SnowballProgram {

        @Override
        public void stem() {
            this.cursor(0);
        }

        void load(final String text) {
            this.current(text);
        }

        void place(final int start, final int lower, final int upper) {
            this.cursor(start);
            this.backward(lower);
            this.limit(upper);
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

        int end() {
            return this.limit();
        }

        int start() {
            return this.bra();
        }

        int stop() {
            return this.ket();
        }

        int lower() {
            return this.backward();
        }

        void mark(final int start, final int end) {
            this.bra(start);
            this.ket(end);
        }

        Object inside(final int min, final int max) {
            return this.inRange(min, max);
        }

        Object insideBack(final int min, final int max) {
            return this.inRangeB(min, max);
        }

        Object outsideRange(final int min, final int max) {
            return this.outRange(min, max);
        }

        Object outsideRangeBack(final int min, final int max) {
            return this.outRangeB(min, max);
        }

        Object grouped(final int[] groups, final int min, final int max) {
            return this.inGrouping(groups, min, max);
        }

        Object groupedBack(final int[] groups, final int min, final int max) {
            return this.inGroupingB(groups, min, max);
        }

        Object ungrouped(final int[] groups, final int min, final int max) {
            return this.outGrouping(groups, min, max);
        }

        Object outBack(final int[] groups, final int min, final int max) {
            return this.outGroupingB(groups, min, max);
        }

        Object equal(final String text) {
            return this.eqS(text);
        }

        Object equalBack(final String text) {
            return this.eqSB(text);
        }

        Object equalV(final String text) {
            return this.eqV(CharBuffer.wrap(text));
        }

        Object equalVBack(final String text) {
            return this.eqVB(CharBuffer.wrap(text));
        }

        Object back() {
            return this.prevChar();
        }

        Object ahead() {
            return this.nextChar();
        }

        int among(final Among... table) {
            return this.findAmong(table);
        }

        int amongBack(final Among... table) {
            return this.findAmongB(table);
        }

        int replace(final int start, final int end, final String text) {
            return this.replaceS(start, end, text);
        }

        void slice(final String text) {
            this.sliceFrom(text);
        }
    }
}
