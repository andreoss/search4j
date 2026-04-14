/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file, the posting lists and the term
 * lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.backend.ValueStats;
import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.types.TermCount;
import io.github.andreoss.search4j.core.types.ValueNo;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Statistics of the slots changed since the last merge, each read from
 * the table when first touched.
 *
 * @since 0.1.0
 */
final class PendingStats {

    /**
     * Statistics by slot, in slot order.
     */
    private final Map<ValueNo, ValueStats> pending;

    /**
     * Statistics stored for a slot.
     */
    private final Function<ValueNo, ValueStats> stored;

    /**
     * Creates the statistics over a lookup.
     *
     * @param stored Statistics stored for a slot
     */
    PendingStats(final Function<ValueNo, ValueStats> stored) {
        this.pending = new TreeMap<>();
        this.stored = stored;
    }

    /**
     * Statistics of a slot as changed so far.
     *
     * @param slot Slot number
     * @return Statistics, empty when the slot was not touched
     */
    Optional<ValueStats> get(final ValueNo slot) {
        return Optional.ofNullable(this.pending.get(slot));
    }

    /**
     * Counts a value added to a slot.
     *
     * @param slot Slot number
     * @param value Value added
     */
    void added(final ValueNo slot, final String value) {
        final ValueStats current = this.current(slot);
        final ValueStats next;
        if (current.frequency().value() == 0) {
            next = new ValueStats(new TermCount(1), value, value);
        } else {
            String lower = current.lower();
            String upper = current.upper();
            if (TermOrder.INSTANCE.compare(value, lower) < 0) {
                lower = value;
            } else if (TermOrder.INSTANCE.compare(value, upper) > 0) {
                upper = value;
            }
            next = new ValueStats(
                current.frequency().plus(new TermCount(1)), lower, upper
            );
        }
        this.pending.put(slot, next);
    }

    /**
     * Counts a value removed from a slot.
     *
     * @param slot Slot number
     */
    void removed(final ValueNo slot) {
        final ValueStats current = this.current(slot);
        final long freq = current.frequency().value();
        if (freq == 0L) {
            throw new DatabaseCorruptError(
                String.format("Value frequency of slot %s below zero", slot)
            );
        }
        ValueStats next = ValueStats.NONE;
        if (freq > 1L) {
            next = new ValueStats(
                new TermCount(freq - 1L), current.lower(), current.upper()
            );
        }
        this.pending.put(slot, next);
    }

    /**
     * Writes the statistics changed and forgets them.
     *
     * @param table Table written
     */
    void write(final TableWriter table) {
        for (final Map.Entry<ValueNo, ValueStats> entry : this.pending.entrySet()) {
            final byte[] key = ValueKeys.stats(entry.getKey());
            if (entry.getValue().frequency().value() == 0) {
                table.delete(key);
            } else {
                table.add(key, ValueStatsTag.encode(entry.getValue()));
            }
        }
        this.pending.clear();
    }

    /**
     * Forgets the statistics changed.
     */
    void clear() {
        this.pending.clear();
    }

    private ValueStats current(final ValueNo slot) {
        return this.pending.computeIfAbsent(slot, this.stored);
    }
}
