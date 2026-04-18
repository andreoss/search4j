/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Matcher core of the search engine.
 */
package io.github.andreoss.search4j.core.matcher;

import java.util.Arrays;

/**
 * Furthest reach of each diagonal of the edit distance table for each
 * distance, between a shorter and a longer character sequence.
 *
 * @since 0.10.0
 */
final class Diagonals {

    /**
     * Shorter sequence.
     */
    private final int[] one;

    /**
     * Longer sequence.
     */
    private final int[] two;

    /**
     * Entries column by column.
     */
    private final int[] fkp;

    private Diagonals(final int[] one, final int[] two, final int... fkp) {
        this.one = one;
        this.two = two;
        this.fkp = fkp;
    }

    static Diagonals of(final int[] one, final int... two) {
        final int rows = 2 * two.length + 1;
        final int[] fkp = new int[rows * (two.length + 3) + 1];
        Arrays.fill(fkp, 0x8080_8080);
        final Diagonals result = new Diagonals(one, two, fkp);
        result.set(0, -1, -1);
        for (int diag = 1; diag <= two.length; ++diag) {
            result.set(diag, diag - 1, -1);
            result.set(-diag, diag - 1, diag - 1);
        }
        return result;
    }

    int distance(final int max) {
        final int lendiff = this.two.length - this.one.length;
        int dist = lendiff;
        boolean found = false;
        while (!found && dist <= max) {
            this.round(lendiff, dist);
            found = this.get(lendiff, dist) == this.one.length;
            if (!found) {
                dist = dist + 1;
            }
        }
        return dist;
    }

    private void round(final int lendiff, final int dist) {
        for (int temp = 0; temp != dist; ++temp) {
            final int inc = dist - temp;
            if (Math.abs(lendiff - inc) <= temp) {
                this.calc(lendiff - inc, temp);
            }
            if (Math.abs(lendiff + inc) <= temp) {
                this.calc(lendiff + inc, temp);
            }
        }
        this.calc(lendiff, dist);
    }

    private void calc(final int diag, final int dist) {
        int reach = this.get(diag, dist - 1) + 1;
        if (this.transposed(reach, reach + diag)) {
            reach = reach + 1;
        }
        reach = Math.max(
            reach,
            Math.max(this.get(diag - 1, dist - 1), this.get(diag + 1, dist - 1) + 1)
        );
        while (reach < this.one.length && reach + diag < this.two.length
            && this.one[reach] == this.two[reach + diag]) {
            reach = reach + 1;
        }
        this.set(diag, dist, reach);
    }

    private boolean transposed(final int first, final int second) {
        boolean result = first > 0 && second > 0 && first < this.one.length;
        result = result && second < this.two.length
            && this.one[first - 1] == this.two[second];
        return result && this.one[first] == this.two[second - 1];
    }

    private int get(final int diag, final int dist) {
        return this.fkp[this.index(diag, dist)];
    }

    private void set(final int diag, final int dist, final int value) {
        this.fkp[this.index(diag, dist)] = value;
    }

    private int index(final int diag, final int dist) {
        return diag + this.two.length + (2 * this.two.length + 1) * (dist + 1);
    }
}
