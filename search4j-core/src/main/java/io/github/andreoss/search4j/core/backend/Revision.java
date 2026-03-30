/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

/**
 * Revision of a shard, raised by each commit.
 *
 * @param value Non-negative revision number
 * @since 0.1.0
 */
public record Revision(long value) implements Comparable<Revision> {

    /**
     * Creates a revision.
     *
     * @param value Non-negative revision number
     */
    public Revision {
        if (value < 0L) {
            throw new IllegalArgumentException(
                String.format("Revision must not be negative: %d", value)
            );
        }
    }

    @Override
    public int compareTo(final Revision other) {
        return Long.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(this.value);
    }
}
