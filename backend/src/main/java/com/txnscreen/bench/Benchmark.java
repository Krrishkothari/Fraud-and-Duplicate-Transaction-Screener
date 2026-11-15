package com.txnscreen.bench;

import java.util.List;

/**
 * One comparison between our algorithm and its baseline, run once per input size by the
 * {@link BenchmarkRunner}.
 */
public interface Benchmark {

    /**
     * Returns the name every measurement of this benchmark carries, such as "membership".
     *
     * @return the benchmark name
     */
    String name();

    /**
     * Measures every variant at one input size.
     *
     * @param size the input size, such as the number of keys or rows
     * @param cfg the repeats, warmup iterations and seed to use
     * @return the measurements for this size
     */
    List<Measurement> run(int size, BenchConfig cfg);
}
