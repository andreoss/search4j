/*
 * SPDX-FileCopyrightText: Copyright (c) 2026
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/**
 * JMH benchmarks from the Maven benchmark profile.
 *
 * @since 0.1.0
 */
package io.github.andreoss.search4j.core.bench;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Runs all JMH benchmarks.
 *
 * @since 0.1.0
 */
public final class RunBenchmarks {

    /**
     * Private constructor.
     */
    private RunBenchmarks() {
    }

    /**
     * Runs all benchmarks.
     *
     * @param args Command line arguments (ignored)
     * @throws RunnerException If the runner fails
     * @since 0.1.0
     */
    public static void main(final String... args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(".*Benchmark.*")
                .forks(1)
                .warmupIterations(1)
                .measurementIterations(1)
                .warmupTime(TimeValue.seconds(1))
                .measurementTime(TimeValue.seconds(1))
                .resultFormat(ResultFormatType.JSON)
                .result("target/jmh-result.json")
                .jvmArgsAppend("-Djava.io.tmpdir=scratch/tmp", "-XX:-UsePerfData")
                .build()
        ).run();
    }
}
