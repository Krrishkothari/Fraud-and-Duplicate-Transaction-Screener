package com.txnscreen.bench;

import java.util.List;
import java.util.Random;

import com.txnscreen.algo.segtree.NodeStats;
import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.baseline.LinearRangeScan;
import com.txnscreen.data.SyntheticDataGenerator;
import com.txnscreen.domain.Transaction;

/**
 * Compares time-window queries on our {@link TimeWindowIndex}, a segment tree over 60-second buckets, against
 * the {@link LinearRangeScan} baseline, on n rows from the {@link SyntheticDataGenerator} with the run's seed.
 * It measures the index build time, the average time of 1,000 random window queries for both methods, the
 * time of one segment tree point update over 10,000 updates, and the bucket count B, so query time can be
 * plotted against log2(B).
 *
 * <p>The index answers whole buckets: a window covers every bucket it touches. So every window here starts
 * at the first second of a bucket and ends at the last second of a bucket, which makes the exact per-second
 * scan and the bucket answer cover the same transactions. Every window's sum, count and maximum must then be
 * identical between the two methods, and the benchmark throws before writing any result if one is not.
 *
 * <p>The generated data always spans 30 days, so B is about 43,200 at every size while n grows. A segment
 * tree query costs O(log B) whatever n is, and a linear scan costs O(n), so the gap widens with n.
 *
 * <p>Point updates add copies of randomly chosen existing rows to a separate index, so every update lands
 * inside the current buckets and measures the O(log B) update alone, never a capacity rebuild. The windows
 * and the update rows are drawn from the seed, so every run measures the same inputs.
 */
public final class RangeBench implements Benchmark {

    /** The bucket width every index in this benchmark uses, the same as the run default. */
    static final int BUCKET_SECONDS = 60;

    /** The number of random windows queried per repeat. */
    static final int WINDOW_COUNT = 1_000;

    /** The number of point updates per repeat. */
    static final int UPDATE_COUNT = 10_000;

    private static final String NAME = "range";
    private static final String SEGMENT_TREE = "segtree";
    private static final String LINEAR_SCAN = "linearscan";
    private static final double DUPLICATE_RATE = 0.02;
    private static final double BLACKLIST_RATE = 0.05;
    private static final double NANOS_PER_MILLI = 1_000_000;
    private static final double NANOS_PER_MICRO = 1_000;

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Measures the segment tree index and the linear scan baseline on size generated rows.
     *
     * <p>Time complexity: O(n log n) to generate the rows, then O((warmup + repeats) * (n + B)) for the
     * builds, O((warmup + repeats) * W * log B) for the tree queries, O((warmup + repeats) * W * n) for the
     * scans and O((warmup + repeats) * U * log B) for the updates, with W = 1,000 windows and U = 10,000
     * updates. Space complexity: O(n + B + W + U).
     *
     * @param size the number of generated rows n
     * @param cfg the repeats, warmup iterations and the seed, used for the data, the windows and the updates
     * @return the measurements for both variants
     * @throws IllegalStateException if any window gives different results from the two methods
     */
    @Override
    public List<Measurement> run(int size, BenchConfig cfg) {
        List<Transaction> rows =
                SyntheticDataGenerator.generate(size, cfg.seed(), DUPLICATE_RATE, BLACKLIST_RATE).rows();
        Timer.Timing build = Timer.time(cfg, () -> TimeWindowIndex.build(rows, BUCKET_SECONDS));
        TimeWindowIndex index = TimeWindowIndex.build(rows, BUCKET_SECONDS);
        LinearRangeScan scan = new LinearRangeScan(rows);

        Random random = new Random(cfg.seed());
        Window[] windows = bucketAlignedWindows(index, WINDOW_COUNT, random);
        requireIdenticalResults(index, scan, windows);
        Timer.Timing treeQueries = Timer.time(cfg, () -> queryAll(index, windows));
        Timer.Timing scanQueries = Timer.time(cfg, () -> queryAll(scan, windows));

        Transaction[] updates = randomRows(rows, UPDATE_COUNT, random);
        TimeWindowIndex updated = TimeWindowIndex.build(rows, BUCKET_SECONDS);
        Timer.Timing pointUpdates = Timer.time(cfg, () -> addAll(updated, updates));

        double nanosPerQueryMicro = WINDOW_COUNT * NANOS_PER_MICRO;
        return List.of(
                Measurement.single(NAME, SEGMENT_TREE, size, "bucketCount", "count", index.bucketCount()),
                build.toMeasurement(NAME, SEGMENT_TREE, size, "buildTime", "ms", NANOS_PER_MILLI),
                treeQueries.toMeasurement(NAME, SEGMENT_TREE, size, "queryTime", "us/query", nanosPerQueryMicro),
                pointUpdates.toMeasurement(NAME, SEGMENT_TREE, size, "updateTime", "ns/update", UPDATE_COUNT),
                // requireIdenticalResults has just confirmed every window matches.
                Measurement.single(NAME, SEGMENT_TREE, size, "mismatchedWindows", "count", 0),
                scanQueries.toMeasurement(NAME, LINEAR_SCAN, size, "queryTime", "us/query", nanosPerQueryMicro));
    }

    /**
     * Draws random windows that each start at the first second of a bucket and end at the last second of the
     * same or a later bucket, with both buckets chosen uniformly from the index's buckets.
     *
     * <p>Time complexity: O(count). Space complexity: O(count).
     *
     * @param index the index whose buckets the windows follow
     * @param count the number of windows
     * @param random the source of the bucket choices
     * @return the windows, in the order drawn
     */
    static Window[] bucketAlignedWindows(TimeWindowIndex index, int count, Random random) {
        int width = index.bucketSeconds();
        long firstBucketStart = Math.floorDiv(index.dataStart(), width) * width;
        Window[] windows = new Window[count];
        for (int i = 0; i < count; i++) {
            int a = random.nextInt(index.bucketCount());
            int b = random.nextInt(index.bucketCount());
            long from = firstBucketStart + (long) Math.min(a, b) * width;
            long to = firstBucketStart + (long) (Math.max(a, b) + 1) * width - 1;
            windows[i] = new Window(from, to);
        }
        return windows;
    }

    /**
     * Throws if the segment tree and the linear scan disagree on any window. A benchmark that saw a mismatch
     * is timing a wrong answer, so it must not write results.
     *
     * <p>Time complexity: O(W * (n + log B)). Space complexity: O(1).
     *
     * @param index the segment tree index
     * @param scan the linear scan over the same rows
     * @param windows bucket-aligned windows inside the data range
     * @throws IllegalStateException if any window's sum, count or maximum differs
     */
    static void requireIdenticalResults(TimeWindowIndex index, LinearRangeScan scan, Window[] windows) {
        for (Window window : windows) {
            NodeStats fromTree = index.query(window.from(), window.to()).stats();
            NodeStats fromScan = scan.query(window.from(), window.to());
            if (!fromTree.equals(fromScan)) {
                throw new IllegalStateException("Window " + window.from() + " to " + window.to()
                        + " gave " + fromTree + " from the segment tree but " + fromScan + " from the linear scan.");
            }
        }
    }

    private static long queryAll(TimeWindowIndex index, Window[] windows) {
        long checksum = 0;
        for (Window window : windows) {
            NodeStats stats = index.query(window.from(), window.to()).stats();
            checksum += stats.sum() + stats.count() + stats.max();
        }
        return checksum;
    }

    private static long queryAll(LinearRangeScan scan, Window[] windows) {
        long checksum = 0;
        for (Window window : windows) {
            NodeStats stats = scan.query(window.from(), window.to());
            checksum += stats.sum() + stats.count() + stats.max();
        }
        return checksum;
    }

    private static Transaction[] randomRows(List<Transaction> rows, int count, Random random) {
        Transaction[] picked = new Transaction[count];
        for (int i = 0; i < count; i++) {
            picked[i] = rows.get(random.nextInt(rows.size()));
        }
        return picked;
    }

    private static TimeWindowIndex addAll(TimeWindowIndex index, Transaction[] updates) {
        for (Transaction t : updates) {
            index.add(t);
        }
        return index;
    }

    /**
     * One query window, both ends inclusive.
     *
     * @param from the window start, in epoch seconds
     * @param to the window end, in epoch seconds
     */
    record Window(long from, long to) {
    }
}
