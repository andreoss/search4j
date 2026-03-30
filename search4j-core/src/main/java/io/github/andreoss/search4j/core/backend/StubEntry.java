/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

/**
 * One database named by a line of a stub file.
 *
 * @since 0.1.0
 */
public sealed interface StubEntry permits StubLocal, StubProgram, StubTcp,
    StubMemory {
}
