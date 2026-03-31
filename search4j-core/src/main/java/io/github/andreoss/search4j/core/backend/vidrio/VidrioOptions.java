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

import io.github.andreoss.search4j.core.error.InvalidArgumentError;
import io.github.andreoss.search4j.core.types.DatabaseAction;
import io.github.andreoss.search4j.core.types.DbFlag;
import java.util.Objects;

/**
 * How a writable vidrio database is opened: the opening flags, the block
 * size of new tables and the number of document changes buffered before
 * they are flushed.
 *
 * <p>The flush threshold comes from the system property
 * {@value #PROPERTY} when set, 10000 otherwise.</p>
 *
 * @param flags Opening flags: action, backend and option bits
 * @param size Block size of the tables created
 * @param threshold Document changes buffered before a flush, above 0
 * @since 0.1.0
 */
public record VidrioOptions(int flags, BlockSize size, int threshold) {

    /**
     * System property naming the flush threshold.
     */
    public static final String PROPERTY = "search4j.flushThreshold";

    /**
     * Creates the options.
     *
     * @param flags Opening flags
     * @param size Block size of the tables created
     * @param threshold Document changes buffered before a flush, above 0
     */
    public VidrioOptions {
        Objects.requireNonNull(size, "size");
        if (threshold <= 0) {
            throw new IllegalArgumentException(
                String.format("Flush threshold must be positive: %d", threshold)
            );
        }
    }

    /**
     * Options from opening flags: the default block size and the flush
     * threshold of the system property.
     *
     * @param flags Opening flags
     * @return Options with the change
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static VidrioOptions of(final int flags) {
        return new VidrioOptions(
            flags, BlockSize.DEFAULT,
            VidrioOptions.property(
                System.getProperty(VidrioOptions.PROPERTY, "")
            )
        );
    }

    /**
     * Flush threshold named by a property value.
     *
     * @param text Property value, empty or 0 for the default
     * @return Threshold above 0
     */
    @SuppressWarnings("PMD.ProhibitPublicStaticMethods")
    public static int property(final String text) {
        int result = 0;
        if (!text.isEmpty()) {
            if (!text.matches("[0-9]{1,9}")) {
                throw new InvalidArgumentError(
                    String.format(
                        "%s must be a non-negative integer",
                        VidrioOptions.PROPERTY
                    )
                );
            }
            result = Integer.parseInt(text);
        }
        if (result == 0) {
            result = 10_000;
        }
        return result;
    }

    /**
     * Action of the flags.
     *
     * @return Action bits of the flags
     */
    public DatabaseAction action() {
        return DatabaseAction.of(this.flags).orElseThrow();
    }

    /**
     * Whether to wait for a write lock held elsewhere.
     *
     * @return True when the retry flag is set
     */
    public boolean retry() {
        return DbFlag.RETRY_LOCK.present(this.flags);
    }

    /**
     * Options with another block size; a size outside the format's range
     * or not a power of two becomes the default.
     *
     * @param bytes Block size in bytes
     * @return Options with the change
     */
    public VidrioOptions withBlockSize(final int bytes) {
        return new VidrioOptions(
            this.flags, BlockSize.orDefault(bytes), this.threshold
        );
    }

    /**
     * Options with another flush threshold.
     *
     * @param count Document changes buffered before a flush, above 0
     * @return Options with the change
     */
    public VidrioOptions withThreshold(final int count) {
        return new VidrioOptions(this.flags, this.size, count);
    }
}
