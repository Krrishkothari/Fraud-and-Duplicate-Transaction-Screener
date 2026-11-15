package com.txnscreen.bench;

/**
 * One measured value. Timings keep the median, minimum and maximum over the repeats; a value measured once,
 * such as memory or a false positive rate, has all three equal and 1 repeat.
 *
 * @param benchmark the benchmark it belongs to, such as "membership"
 * @param variant what was measured, such as "bloom" or "hashset"
 * @param size the input size
 * @param metric what the value is, such as "insertTime" or "memory"
 * @param unit the unit of the values, such as "ms", "ns/query" or "bytes"
 * @param median the median value
 * @param min the smallest value
 * @param max the largest value
 * @param repeats the number of timed iterations behind the values
 */
public record Measurement(
        String benchmark,
        String variant,
        int size,
        String metric,
        String unit,
        double median,
        double min,
        double max,
        int repeats) {

    /**
     * Creates a measurement of a value that is not timed, so it is measured once.
     *
     * @param benchmark the benchmark it belongs to
     * @param variant what was measured
     * @param size the input size
     * @param metric what the value is
     * @param unit the unit of the value
     * @param value the value
     * @return the measurement
     */
    public static Measurement single(String benchmark, String variant, int size, String metric, String unit,
            double value) {
        return new Measurement(benchmark, variant, size, metric, unit, value, value, value, 1);
    }
}
