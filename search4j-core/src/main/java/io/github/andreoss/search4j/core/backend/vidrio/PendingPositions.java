/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * On-disk tables of the vidrio backend: format definitions, the read and
 * write paths of a table, the version file and the posting lists.
 */
package io.github.andreoss.search4j.core.backend.vidrio;

import io.github.andreoss.search4j.core.backend.TermOrder;
import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DocId;
import io.github.andreoss.search4j.core.types.TermPos;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Position list changes buffered until the next flush, encoded as the
 * position table stores them.
 *
 * <p>Position 0 is refused: the encoding cannot hold it next to position
 * 1.</p>
 *
 * @since 0.1.0
 */
public final class PendingPositions {

    /**
     * Encoded positions by term and document, empty for a list removed.
     */
    private final NavigableMap<String, NavigableMap<DocId, byte[]>> changes;

    /**
     * Whether positions are known to be present: 1 when they are, 0 when
     * they are not, -1 when unknown.
     */
    private int known;

    /**
     * Creates empty changes.
     */
    public PendingPositions() {
        this.changes = new TreeMap<>(TermOrder.INSTANCE);
        this.known = -1;
    }

    /**
     * Sets the positions of a term in a document.
     *
     * @param doc Document
     * @param term Term text
     * @param positions Ascending positions above 0, at least one
     */
    public void set(
        final DocId doc, final String term, final List<TermPos> positions
    ) {
        this.put(doc, term, PendingPositions.encode(positions));
    }

    /**
     * Removes the positions of a term in a document.
     *
     * @param doc Document
     * @param term Term text
     */
    public void delete(final DocId doc, final String term) {
        this.put(doc, term, new byte[0]);
    }

    /**
     * Sets the positions of a term in a document that may hold some
     * already: a list equal to the one pending or stored is not recorded
     * again, an empty list removes the positions.
     *
     * @param table Position table as stored
     * @param key Document and term
     * @param positions Ascending positions above 0
     */
    public void store(
        final TableLookup table, final PositionKey key,
        final List<TermPos> positions
    ) {
        if (positions.isEmpty()) {
            this.delete(key.doc(), key.term());
        } else {
            final byte[] tag = PendingPositions.encode(positions);
            final NavigableMap<DocId, byte[]> pending =
                this.changes.get(key.term());
            if (pending != null && pending.containsKey(key.doc())) {
                pending.put(key.doc(), tag);
                this.known = 1;
            } else if (!Arrays.equals(
                tag,
                table.get(PositionKeys.of(key.doc(), key.term()))
                    .orElseGet(() -> new byte[0])
            )) {
                this.put(key.doc(), key.term(), tag);
            }
        }
    }

    /**
     * Positions of a term in a document as changed so far.
     *
     * @param doc Document
     * @param term Term text
     * @return Encoded positions, empty bytes for a list removed, empty
     *  when no change is pending
     */
    public Optional<byte[]> get(final DocId doc, final String term) {
        return Optional.ofNullable(this.changes.get(term))
            .map(pending -> pending.get(doc));
    }

    /**
     * Tests whether positions are present once the changes are applied.
     *
     * @param stored Number of position lists stored
     * @return True when a list is pending or one stored survives
     */
    public boolean hasPositions(final EntryCount stored) {
        if (this.known < 0) {
            long removed = 0L;
            for (final NavigableMap<DocId, byte[]> pending
                : this.changes.values()) {
                for (final byte[] tag : pending.values()) {
                    if (tag.length > 0) {
                        this.known = 1;
                    }
                    removed += 1L;
                }
            }
            if (this.known < 0 && removed == stored.value()) {
                this.known = 0;
            } else if (this.known < 0) {
                this.known = 1;
            }
        }
        return this.known == 1;
    }

    /**
     * Tests whether changes are pending.
     *
     * @return True when a list was set or removed since the last flush
     */
    public boolean pending() {
        return !this.changes.isEmpty();
    }

    /**
     * Writes the changes to a position table and forgets them.
     *
     * @param table Table written
     */
    public void flush(final TableWriter table) {
        for (final Map.Entry<String, NavigableMap<DocId, byte[]>> entry
            : this.changes.entrySet()) {
            for (final Map.Entry<DocId, byte[]> change
                : entry.getValue().entrySet()) {
                final byte[] key = PositionKeys.of(change.getKey(), entry.getKey());
                if (change.getValue().length == 0) {
                    table.delete(key);
                } else {
                    table.add(key, change.getValue());
                }
            }
        }
        this.clear();
    }

    /**
     * Forgets the changes.
     */
    public void clear() {
        this.changes.clear();
        this.known = -1;
    }

    private void put(final DocId doc, final String term, final byte[] tag) {
        if (tag.length == 0) {
            this.known = -1;
        } else {
            this.known = 1;
        }
        this.changes.computeIfAbsent(term, key -> new TreeMap<>()).put(doc, tag);
    }

    private static byte[] encode(final List<TermPos> positions) {
        if (!positions.isEmpty() && positions.get(0).value() == 0) {
            throw new InvalidArgumentError(
                "Position 0 cannot be stored: positions start at 1"
            );
        }
        return PositionTag.encode(positions);
    }
}
