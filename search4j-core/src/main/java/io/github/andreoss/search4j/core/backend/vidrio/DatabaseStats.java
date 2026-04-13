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

import io.github.andreoss.search4j.core.error.DatabaseCorruptError;
import io.github.andreoss.search4j.core.error.InvariantError;
import io.github.andreoss.search4j.core.error.RangeError;
import io.github.andreoss.search4j.core.ser.Varints;
import java.io.ByteArrayOutputStream;

/**
 * Statistics of a database kept in its version record.
 *
 * <p>Counts and bounds are unsigned 32-bit values, the total length an
 * unsigned 64-bit one; every value is immutable and each update returns a
 * new record.</p>
 *
 * @param docs Number of documents
 * @param last Greatest document number ever used
 * @param total Total of the lengths of every document
 * @param lower Lower bound on the smallest document length
 * @param upper Upper bound on the greatest document length
 * @param wdf Upper bound on the greatest within-document frequency
 * @param spelling Upper bound on the spelling word frequency
 * @param changeset Oldest change set kept, 0 for none
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public record DatabaseStats(
    long docs, long last, long total, long lower, long upper, long wdf,
    long spelling, long changeset
) {

    /**
     * The statistics of an empty database.
     */
    public static final DatabaseStats EMPTY =
        new DatabaseStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

    /**
     * Largest 32-bit value.
     */
    private static final long NARROW = 0xFFFF_FFFFL;

    /**
     * Creates the statistics.
     *
     * @param docs Number of documents, 32 bits
     * @param last Greatest document number, 32 bits
     * @param total Total document length, non-negative
     * @param lower Lower bound on the document length, 32 bits
     * @param upper Upper bound on the document length, 32 bits
     * @param wdf Upper bound on the within-document frequency, 32 bits
     * @param spelling Upper bound on the spelling word frequency, 32 bits
     * @param changeset Oldest change set, 32 bits
     */
    public DatabaseStats {
        DatabaseStats.narrow(docs, "Document count");
        DatabaseStats.narrow(last, "Last document number");
        DatabaseStats.narrow(lower, "Document length lower bound");
        DatabaseStats.narrow(upper, "Document length upper bound");
        DatabaseStats.narrow(wdf, "Wdf upper bound");
        DatabaseStats.narrow(spelling, "Spelling frequency upper bound");
        DatabaseStats.narrow(changeset, "Oldest change set");
        if (total < 0L) {
            throw new IllegalArgumentException(
                String.format("Total length out of range: %d", total)
            );
        }
    }

    /**
     * Decodes the statistics stored after the root records.
     *
     * @param reader Bytes at the statistics
     * @param embedded Whether more data follows in the same file
     * @return Statistics, {@link #EMPTY} for no bytes
     */
    public static DatabaseStats parse(
        final ByteReader reader, final boolean embedded
    ) {
        final DatabaseStats stats;
        if (reader.done()) {
            stats = DatabaseStats.EMPTY;
        } else {
            final String what = "Bad serialised DB stats";
            final long docs = reader.narrow(what);
            final long last = reader.narrow(what);
            final long lower = reader.narrow(what);
            final long wdf = reader.narrow(what);
            final long upper = reader.narrow(what);
            final long changeset = reader.narrow(what);
            final long total = reader.wide(what);
            final long spelling = reader.narrow(what);
            if (!embedded && !reader.done()) {
                throw new DatabaseCorruptError(
                    "Version record has junk at end"
                );
            }
            if (last + docs > DatabaseStats.NARROW || total < 0L) {
                throw new DatabaseCorruptError(
                    "Bad serialised DB stats (overflowed)"
                );
            }
            stats = new DatabaseStats(
                docs, last + docs, total, lower,
                (upper + wdf) & DatabaseStats.NARROW, wdf, spelling,
                changeset
            );
        }
        return stats;
    }

    /**
     * Encodes the statistics as stored in the version record.
     *
     * @return Eight variable-length integers
     */
    public byte[] bytes() {
        if (this.last < this.docs) {
            throw new InvariantError(
                String.format("Statistics out of order: %s", this)
            );
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(Varints.pack(this.docs));
        out.writeBytes(Varints.pack(this.last - this.docs));
        out.writeBytes(Varints.pack(this.lower));
        out.writeBytes(Varints.pack(this.wdf));
        out.writeBytes(
            Varints.pack((this.upper - this.wdf) & DatabaseStats.NARROW)
        );
        out.writeBytes(Varints.pack(this.changeset));
        out.writeBytes(Varints.pack(this.total));
        out.writeBytes(Varints.pack(this.spelling));
        return out.toByteArray();
    }

    /**
     * Counts a document added.
     *
     * @param length Length of the document
     * @return Statistics with the document
     */
    public DatabaseStats added(final long length) {
        final long least;
        if (this.lower == 0L || length != 0L && length < this.lower) {
            least = length;
        } else {
            least = this.lower;
        }
        return new DatabaseStats(
            DatabaseStats.count(this.docs + 1L, "Document count"), this.last,
            DatabaseStats.sum(this.total + length, "Total length"), least,
            Math.max(this.upper, length), this.wdf, this.spelling,
            this.changeset
        );
    }

    /**
     * Counts a document deleted; the bounds reset once no length is left.
     *
     * @param length Length of the document
     * @return Statistics without the document
     */
    public DatabaseStats deleted(final long length) {
        if (this.docs == 0L || length > this.total) {
            throw new InvariantError(
                String.format("No document of length %d to delete", length)
            );
        }
        final long left = this.total - length;
        final DatabaseStats stats;
        if (left == 0L) {
            stats = new DatabaseStats(
                this.docs - 1L, this.last, 0L, 0L, 0L, 0L, this.spelling,
                this.changeset
            );
        } else {
            stats = new DatabaseStats(
                this.docs - 1L, this.last, left, this.lower, this.upper,
                this.wdf, this.spelling, this.changeset
            );
        }
        return stats;
    }

    /**
     * Raises the within-document frequency bound to a frequency seen.
     *
     * @param frequency Frequency seen
     * @return Statistics with the bound at least the frequency
     */
    public DatabaseStats checked(final long frequency) {
        final DatabaseStats stats;
        if (frequency > this.wdf) {
            stats = new DatabaseStats(
                this.docs, this.last, this.total, this.lower, this.upper,
                frequency, this.spelling, this.changeset
            );
        } else {
            stats = this;
        }
        return stats;
    }

    /**
     * Sets the greatest document number used.
     *
     * @param number Document number
     * @return Statistics with the number
     */
    public DatabaseStats withLast(final long number) {
        return new DatabaseStats(
            this.docs, number, this.total, this.lower, this.upper, this.wdf,
            this.spelling, this.changeset
        );
    }

    /**
     * Sets the spelling word frequency bound.
     *
     * @param bound Upper bound
     * @return Statistics with the bound
     */
    public DatabaseStats withSpelling(final long bound) {
        return new DatabaseStats(
            this.docs, this.last, this.total, this.lower, this.upper,
            this.wdf, bound, this.changeset
        );
    }

    /**
     * Sets the oldest change set kept.
     *
     * @param oldest Change set number
     * @return Statistics with the change set
     */
    public DatabaseStats withChangeset(final long oldest) {
        return new DatabaseStats(
            this.docs, this.last, this.total, this.lower, this.upper,
            this.wdf, this.spelling, oldest
        );
    }

    /**
     * Lower bound on the number of unique terms of a document: the length
     * lower bound divided by the frequency upper bound, rounded up.
     *
     * @return Bound, 0 when the total length is 0
     */
    public long uniqueTermsLowerBound() {
        if (this.total != 0L && (this.lower == 0L || this.wdf == 0L)) {
            throw new DatabaseCorruptError(
                "Length bounds inconsistent with the total length"
            );
        }
        final long bound;
        if (this.total == 0L) {
            bound = 0L;
        } else {
            bound = (this.lower - 1L) / this.wdf + 1L;
        }
        return bound;
    }

    /**
     * Merges the statistics of another database, as compaction does; the
     * greatest document number and the change set stay.
     *
     * @param other Statistics merged in
     * @return Statistics of both
     */
    public DatabaseStats merged(final DatabaseStats other) {
        final long least;
        if (other.lower > 0L
            && (this.lower == 0L || other.lower < this.lower)) {
            least = other.lower;
        } else {
            least = this.lower;
        }
        return new DatabaseStats(
            DatabaseStats.count(this.docs + other.docs, "Document count"),
            this.last,
            DatabaseStats.sum(this.total + other.total, "Total length"),
            least, Math.max(this.upper, other.upper),
            Math.max(this.wdf, other.wdf),
            DatabaseStats.count(
                this.spelling + other.spelling,
                "Spelling frequency upper bound"
            ),
            this.changeset
        );
    }

    private static long sum(final long value, final String what) {
        if (value < 0L) {
            throw new RangeError(String.format("%s overflowed", what));
        }
        return value;
    }

    private static long count(final long value, final String what) {
        if (value > DatabaseStats.NARROW) {
            throw new RangeError(String.format("%s overflowed", what));
        }
        return value;
    }

    private static void narrow(final long value, final String what) {
        if (value < 0L || value > DatabaseStats.NARROW) {
            throw new IllegalArgumentException(
                String.format("%s out of range: %d", what, value)
            );
        }
    }
}
