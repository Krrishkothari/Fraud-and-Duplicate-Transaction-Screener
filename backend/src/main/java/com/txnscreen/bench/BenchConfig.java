package com.txnscreen.bench;

/**
 * The settings every benchmark uses.
 *
 * @param repeats the number of timed iterations, at least 1
 * @param warmup the number of untimed iterations run first so the JIT compiles the code, at least 0
 * @param seed the seed for generated data and random choices, so every run measures the same inputs
 */
public record BenchConfig(int repeats, int warmup, long seed) {

    public BenchConfig {
        if (repeats < 1) {
            throw new IllegalArgumentException("repeats must be at least 1, got " + repeats);
        }
        if (warmup < 0) {
            throw new IllegalArgumentException("warmup must be 0 or more, got " + warmup);
        }
    }
}
