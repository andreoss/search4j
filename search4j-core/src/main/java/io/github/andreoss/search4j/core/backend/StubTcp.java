/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

/**
 * Stub line naming a remote database reached over TCP.
 *
 * @param host Host name or address, without brackets
 * @param port Port number
 * @since 0.1.0
 */
public record StubTcp(String host, int port) implements StubEntry {
}
