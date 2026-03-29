/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Tests of the search engine error exceptions.
 */
package io.github.andreoss.search4j.core.error;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the dispatch table reconstructs every error type.
 *
 * @since 0.1.0
 */
final class ErrorDispatchTest {

    @Test
    void reconstructsInvariant() {
        Assertions.assertEquals(
            "InvariantError",
            ErrorDispatch.of("InvariantError", "msg").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsInvalidArgument() {
        Assertions.assertEquals(
            "InvalidArgumentError",
            ErrorDispatch.of("InvalidArgumentError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsInvalidOperation() {
        Assertions.assertEquals(
            "InvalidOperationError",
            ErrorDispatch.of("InvalidOperationError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsUnimplemented() {
        Assertions.assertEquals(
            "UnimplementedError",
            ErrorDispatch.of("UnimplementedError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseCorrupt() {
        Assertions.assertEquals(
            "DatabaseCorruptError",
            ErrorDispatch.of("DatabaseCorruptError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseCreate() {
        Assertions.assertEquals(
            "DatabaseCreateError",
            ErrorDispatch.of("DatabaseCreateError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseLock() {
        Assertions.assertEquals(
            "DatabaseLockError",
            ErrorDispatch.of("DatabaseLockError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseModified() {
        Assertions.assertEquals(
            "DatabaseModifiedError",
            ErrorDispatch.of("DatabaseModifiedError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseVersion() {
        Assertions.assertEquals(
            "DatabaseVersionError",
            ErrorDispatch.of("DatabaseVersionError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseNotFound() {
        Assertions.assertEquals(
            "DatabaseNotFoundError",
            ErrorDispatch.of("DatabaseNotFoundError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDatabaseClosed() {
        Assertions.assertEquals(
            "DatabaseClosedError",
            ErrorDispatch.of("DatabaseClosedError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsDocNotFound() {
        Assertions.assertEquals(
            "DocNotFoundError",
            ErrorDispatch.of("DocNotFoundError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsFeatureUnavailable() {
        Assertions.assertEquals(
            "FeatureUnavailableError",
            ErrorDispatch.of("FeatureUnavailableError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsEngineInternal() {
        Assertions.assertEquals(
            "EngineInternalError",
            ErrorDispatch.of("EngineInternalError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsNetwork() {
        Assertions.assertEquals(
            "NetworkError",
            ErrorDispatch.of("NetworkError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsNetworkTimeout() {
        Assertions.assertEquals(
            "NetworkTimeoutError",
            ErrorDispatch.of("NetworkTimeoutError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsQueryParser() {
        Assertions.assertEquals(
            "QueryParserError",
            ErrorDispatch.of("QueryParserError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsSerialisation() {
        Assertions.assertEquals(
            "SerialisationError",
            ErrorDispatch.of("SerialisationError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsRange() {
        Assertions.assertEquals(
            "RangeError",
            ErrorDispatch.of("RangeError", "m").getType(),
            "Type"
        );
    }

    @Test
    void reconstructsWildcard() {
        Assertions.assertEquals(
            "WildcardError",
            ErrorDispatch.of("WildcardError", "m").getType(),
            "Type"
        );
    }

    @Test
    void keepsMessage() {
        Assertions.assertEquals(
            "specific",
            ErrorDispatch.of("RangeError", "specific").getMessage(),
            "Message"
        );
    }

    @Test
    void rejectsUnknownType() {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> ErrorDispatch.of("NoSuchError", "m"),
            "Unknown type rejected"
        );
    }

    @Test
    void knowsARealType() {
        Assertions.assertTrue(
            ErrorDispatch.known("DocNotFoundError"), "Known type"
        );
    }

    @Test
    void rejectsUnknownName() {
        Assertions.assertFalse(
            ErrorDispatch.known("NoSuchError"), "Unknown name rejected"
        );
    }
}
