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
 * Tests that every error type carries its name, message, context and number.
 *
 * @since 0.1.0
 */
final class SearchErrorTest {

    @Test
    void assertionCarriesType() {
        Assertions.assertEquals(
            "InvariantError", new InvariantError("bad").getType(),
            "Type name"
        );
    }

    @Test
    void assertionCarriesMessage() {
        Assertions.assertEquals(
            "bad", new InvariantError("bad").getMessage(), "Message"
        );
    }

    @Test
    void contextIsEmptyByDefault() {
        Assertions.assertEquals(
            "", new InvariantError("bad").getContext(), "Empty context"
        );
    }

    @Test
    void contextIsKept() {
        Assertions.assertEquals(
            "ctx", new InvariantError("bad", "ctx").getContext(), "Context"
        );
    }

    @Test
    void errnoIsKept() {
        Assertions.assertEquals(
            3, new InvariantError("bad", 3).getErrno(), "Error number"
        );
    }

    @Test
    void systemMessageNamesNumber() {
        Assertions.assertEquals(
            "system error 3", new InvariantError("bad", 3).getSystemMessage(),
            "System message"
        );
    }

    @Test
    void systemMessageEmptyWithoutNumber() {
        Assertions.assertEquals(
            "", new InvariantError("bad").getSystemMessage(),
            "No system message"
        );
    }

    @Test
    void descriptionHasTypeAndMessage() {
        Assertions.assertTrue(
            new InvariantError("bad").getDescription().contains("InvariantError"),
            "Description contains type"
        );
    }

    @Test
    void logicErrorAssignableToSearchError() {
        Assertions.assertTrue(
            SearchError.class.isAssignableFrom(InvalidArgumentError.class),
            "Logic base"
        );
    }

    @Test
    void runtimeErrorAssignableToSearchError() {
        Assertions.assertTrue(
            SearchError.class.isAssignableFrom(DocNotFoundError.class),
            "Runtime base"
        );
    }

    @Test
    void databaseErrorAssignableToRuntimeError() {
        Assertions.assertTrue(
            RuntimeError.class.isAssignableFrom(DatabaseCorruptError.class),
            "Database base"
        );
    }

    @Test
    void versionErrorAssignableToOpeningError() {
        Assertions.assertTrue(
            DatabaseOpeningError.class.isAssignableFrom(DatabaseVersionError.class),
            "Opening base"
        );
    }

    @Test
    void timeoutAssignableToNetworkError() {
        Assertions.assertTrue(
            NetworkError.class.isAssignableFrom(NetworkTimeoutError.class),
            "Network base"
        );
    }
}
