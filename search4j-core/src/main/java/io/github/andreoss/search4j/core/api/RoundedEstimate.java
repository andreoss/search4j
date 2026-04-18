/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Public API of the search engine.
 */
package io.github.andreoss.search4j.core.api;

import io.github.andreoss.search4j.core.types.TermCount;

/**
 * An estimate of a count rounded to the precision its bounds justify.
 *
 * <p>The step is the largest power of ten not above the smaller of the
 * estimate and the distance between the bounds; below a step of ten the
 * estimate is kept. The estimate goes to the nearer multiple of the step
 * that stays within the bounds, and from the midpoint towards the middle of
 * the bounds.</p>
 *
 * @param lower Lower bound of the count
 * @param estimate Estimate of the count, between the bounds
 * @param upper Upper bound of the count
 * @since 0.7.1
 */
record RoundedEstimate(TermCount lower, TermCount estimate, TermCount upper) {

    /**
     * Mask of an unsigned 32-bit count.
     */
    private static final long MASK = 0xFFFF_FFFFL;

    /**
     * The rounded estimate.
     *
     * @return Estimate rounded within the bounds
     */
    TermCount value() {
        final long low = this.lower.value();
        final long high = this.upper.value();
        final long est = this.estimate.value();
        final long scale = Math.min(high - low & RoundedEstimate.MASK, est);
        long result = est;
        if (scale > 10L) {
            long step = 1L;
            while (step * 10L <= scale) {
                step = step * 10L;
            }
            result = est / step * step;
            final boolean upward;
            if (result < low) {
                upward = true;
            } else if (result > (high - step & RoundedEstimate.MASK)) {
                upward = false;
            } else {
                final long twice = 2L * (est - result);
                final boolean nearer = (result - low & RoundedEstimate.MASK)
                    <= (high - step - result & RoundedEstimate.MASK);
                upward = twice > step || twice == step && nearer;
            }
            if (upward) {
                result = result + step & RoundedEstimate.MASK;
            }
        }
        return new TermCount(result);
    }
}
