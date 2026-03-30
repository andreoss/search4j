/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * Backend contract, support types and the database handle over a shard.
 */
package io.github.andreoss.search4j.core.backend;

import io.github.andreoss.search4j.core.types.Backend;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Stub line naming a database on local disk.
 *
 * @param path Database location, resolved against the stub's directory
 * @param backend Backend named by the line, empty to detect it
 * @since 0.1.0
 */
public record StubLocal(Path path, Optional<Backend> backend)
    implements StubEntry {
}
