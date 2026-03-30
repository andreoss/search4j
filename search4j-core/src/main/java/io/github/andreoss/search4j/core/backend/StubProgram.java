/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

/**
 * Stub line naming a remote database reached through a spawned program.
 *
 * @param program Program to run
 * @param args Arguments passed to it, empty when none
 * @since 0.1.0
 */
public record StubProgram(String program, String args) implements StubEntry {
}
