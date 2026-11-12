package com.txnscreen.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.algo.segtree.WindowResult;

/**
 * Builds each run's time window index and answers window and series queries on it. Every access to the
 * index holds the run's lock, so a live append can never interleave with a query.
 */
@Service
public class AnalyticsService {

    /** The fewest windows a series may have. */
    public static final int MIN_POINTS = 1;

    /** The most windows a series may have. */
    public static final int MAX_POINTS = 500;

    /** The number of windows in a series when none is requested. */
    public static final int DEFAULT_POINTS = 120;

    /**
     * Builds the time window index over a run's valid transactions, with the run's bucket width, and
     * attaches it to the run together with the build time.
     *
     * @param run the run, which has at least one valid transaction
     */
    public void attachIndex(Run run) {
        synchronized (run.lock()) {
            long start = System.nanoTime();
            TimeWindowIndex index = TimeWindowIndex.build(run.transactions(), run.config().bucketSeconds());
            run.setAnalytics(index, micros(start));
        }
    }

    /**
     * Returns the sum, count and maximum for one window of a run.
     *
     * @param run the run
     * @param fromEpoch the window start, in epoch seconds
     * @param toEpoch the window end, in epoch seconds
     * @return the window result and the segment tree query time
     */
    public Timed<WindowResult> window(Run run, long fromEpoch, long toEpoch) {
        return timed(run, index -> index.query(fromEpoch, toEpoch));
    }

    /**
     * Splits a window of a run into consecutive windows for a chart, one segment tree query each. A missing
     * bound defaults to the start or end of the run's data, read under the same lock as the query.
     *
     * @param run the run
     * @param fromEpoch the window start in epoch seconds, or null for the data start
     * @param toEpoch the window end in epoch seconds, or null for the data end
     * @param points the number of windows wanted, from {@link #MIN_POINTS} to {@link #MAX_POINTS}
     * @return the windows in time order and the total query time
     */
    public Timed<List<WindowResult>> series(Run run, Long fromEpoch, Long toEpoch, int points) {
        return timed(run, index -> index.series(fromEpoch == null ? index.dataStart() : fromEpoch,
                toEpoch == null ? index.dataEnd() : toEpoch, points));
    }

    private static <T> Timed<T> timed(Run run, Function<TimeWindowIndex, T> query) {
        synchronized (run.lock()) {
            long start = System.nanoTime();
            T value = query.apply(run.analyticsIndex());
            return new Timed<>(value, micros(start));
        }
    }

    private static long micros(long startNanos) {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanos);
    }
}
