package com.txnscreen.bench;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Times a piece of work with {@link System#nanoTime}: untimed warmup iterations first, then timed repeats.
 * Every result is written to a volatile sink, so the JIT cannot decide the work is unused and remove it.
 */
public final class Timer {

    /** Holds the last result of timed work, only so the work cannot be optimised away. */
    @SuppressWarnings("unused")
    private static volatile Object sink;

    private Timer() {
    }

    /**
     * Runs the work warmup times untimed, then repeats times timed.
     *
     * <p>Time complexity: O((warmup + repeats) * W + repeats log repeats) for work taking W. Space
     * complexity: O(repeats).
     *
     * @param cfg the warmup and repeat counts
     * @param work the work to time; it should return its result so the result can be sunk
     * @return the median, minimum and maximum time in nanoseconds
     */
    public static Timing time(BenchConfig cfg, Supplier<?> work) {
        for (int i = 0; i < cfg.warmup(); i++) {
            sink = work.get();
        }
        long[] nanos = new long[cfg.repeats()];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            sink = work.get();
            nanos[i] = System.nanoTime() - start;
        }
        return Timing.of(nanos);
    }

    /**
     * The spread of one timed piece of work.
     *
     * @param medianNanos the median time; the mean of the two middle times when the count is even
     * @param minNanos the fastest time
     * @param maxNanos the slowest time
     * @param repeats the number of timed iterations
     */
    public record Timing(double medianNanos, double minNanos, double maxNanos, int repeats) {

        static Timing of(long[] nanos) {
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            int middle = sorted.length / 2;
            double median = sorted.length % 2 == 1
                    ? sorted[middle]
                    : (sorted[middle - 1] + sorted[middle]) / 2.0;
            return new Timing(median, sorted[0], sorted[sorted.length - 1], sorted.length);
        }

        /**
         * Converts the timing to a measurement, dividing every time by nanosPerUnit: 1,000,000 for
         * milliseconds, or the number of operations for time per operation.
         *
         * @param benchmark the benchmark it belongs to
         * @param variant what was measured
         * @param size the input size
         * @param metric what the value is
         * @param unit the unit after dividing
         * @param nanosPerUnit the divisor, greater than 0
         * @return the measurement
         */
        public Measurement toMeasurement(String benchmark, String variant, int size, String metric, String unit,
                double nanosPerUnit) {
            return new Measurement(benchmark, variant, size, metric, unit, medianNanos / nanosPerUnit,
                    minNanos / nanosPerUnit, maxNanos / nanosPerUnit, repeats);
        }
    }
}
