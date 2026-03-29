/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Error exceptions of the search engine.
 */
package io.github.andreoss.search4j.core.error;

import java.util.function.Function;

/**
 * Maps error type names to their exception classes.
 *
 * <p>Used by the remote protocol to reconstruct a received error from its
 * serialised type name and message.</p>
 *
 * @since 0.1.0
 */
@SuppressWarnings("PMD.ProhibitPublicStaticMethods")
public final class ErrorDispatch {

    /**
     * The dispatch entries in type-name order.
     */
    private static final Entry[] ENTRIES = {
        entry("InvariantError", InvariantError::new),
        entry("InvalidArgumentError", InvalidArgumentError::new),
        entry("InvalidOperationError", InvalidOperationError::new),
        entry("UnimplementedError", UnimplementedError::new),
        entry("DatabaseCorruptError", DatabaseCorruptError::new),
        entry("DatabaseCreateError", DatabaseCreateError::new),
        entry("DatabaseLockError", DatabaseLockError::new),
        entry("DatabaseModifiedError", DatabaseModifiedError::new),
        entry("DatabaseVersionError", DatabaseVersionError::new),
        entry("DatabaseNotFoundError", DatabaseNotFoundError::new),
        entry("DatabaseClosedError", DatabaseClosedError::new),
        entry("DocNotFoundError", DocNotFoundError::new),
        entry("FeatureUnavailableError", FeatureUnavailableError::new),
        entry("EngineInternalError", EngineInternalError::new),
        entry("NetworkError", NetworkError::new),
        entry("NetworkTimeoutError", NetworkTimeoutError::new),
        entry("QueryParserError", QueryParserError::new),
        entry("SerialisationError", SerialisationError::new),
        entry("RangeError", RangeError::new),
        entry("WildcardError", WildcardError::new),
    };

    /**
     * Utility class, no instances.
     */
    private ErrorDispatch() {
    }

    /**
     * Reconstructs an error from its type name and message.
     *
     * @param type The error type name
     * @param message The human-readable message
     * @return The reconstructed error
     */
    public static SearchError of(final String type, final String message) {
        SearchError result = null;
        for (final Entry entry : ErrorDispatch.ENTRIES) {
            if (entry.type().equals(type)) {
                result = entry.factory().apply(message);
            }
        }
        if (result == null) {
            throw new IllegalArgumentException(
                String.format("Unknown error type: %s", type)
            );
        }
        return result;
    }

    /**
     * Tests whether an error type name is known.
     *
     * @param type The error type name
     * @return True when the type is known
     */
    public static boolean known(final String type) {
        boolean found = false;
        for (final Entry entry : ErrorDispatch.ENTRIES) {
            if (entry.type().equals(type)) {
                found = true;
            }
        }
        return found;
    }

    private static Entry entry(
        final String type,
        final Function<String, SearchError> factory
    ) {
        return new Entry(type, factory);
    }

    /**
     * Pair of a type name and a constructor invocation.
     *
     * @param type The error type name
     * @param factory The construction function
     * @since 0.1.0
     */
    private record Entry(
        String type,
        Function<String, SearchError> factory
    ) {
    }
}
