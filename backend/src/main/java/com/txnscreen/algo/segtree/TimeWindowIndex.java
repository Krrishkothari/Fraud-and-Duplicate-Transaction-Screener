package com.txnscreen.algo.segtree;

import com.txnscreen.api.ApiException;
import com.txnscreen.domain.Transaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Groups transactions into fixed-width time buckets and answers window queries with a
 * {@link SegmentTree} whose leaves are the buckets.
 *
 * <p>With origin t0 (the earliest timestamp rounded down to a multiple of the bucket width w),
 * a timestamp ts falls in bucket (ts - t0) / w. A window query covers every bucket that touches
 * the window, so results are exact to the bucket width.
 *
 * <p>The leaf values are also kept in plain arrays. When a live transaction lands past the last
 * bucket, the capacity doubles until it fits and the tree is rebuilt from those arrays. Each
 * rebuild costs O(B) and at least doubles the capacity, so growth is O(1) amortized per append.
 *
 * <p>Space complexity: O(B), where B is the bucket capacity.
 */
public final class TimeWindowIndex {

    /** Smallest allowed bucket width, in seconds. */
    public static final int MIN_BUCKET_SECONDS = 1;

    /** Largest allowed bucket width, in seconds (one day). */
    public static final int MAX_BUCKET_SECONDS = 86_400;

    /** Most buckets one index may hold, so the arrays stay well inside the server's memory. */
    public static final int MAX_BUCKETS = 1 << 22;

    private final int bucketSeconds;
    private final long t0;
    private long dataStart;
    private long dataEnd;
    private int bucketCount;
    private long[] leafSum;
    private long[] leafCount;
    private long[] leafMax;
    private SegmentTree tree;

    private TimeWindowIndex(int bucketSeconds, long t0, long dataStart, long dataEnd, int bucketCount,
            long[] leafSum, long[] leafCount, long[] leafMax) {
        this.bucketSeconds = bucketSeconds;
        this.t0 = t0;
        this.dataStart = dataStart;
        this.dataEnd = dataEnd;
        this.bucketCount = bucketCount;
        this.leafSum = leafSum;
        this.leafCount = leafCount;
        this.leafMax = leafMax;
        this.tree = SegmentTree.build(leafSum, leafCount, leafMax);
    }

    /**
     * Builds an index over the given transactions, with one leaf per bucket from the earliest
     * to the latest timestamp.
     *
     * <p>Time complexity: O(n + B), for n transactions and B buckets. Space complexity: O(B).
     *
     * @param txns the transactions, in any order; must not be empty
     * @param bucketSeconds the bucket width, from 1 to 86,400 seconds
     * @return the built index
     * @throws IllegalArgumentException if txns is null or empty, or bucketSeconds is out of range
     * @throws ApiException 422 TOO_MANY_BUCKETS if the data spans more than {@link #MAX_BUCKETS}
     *     buckets
     */
    public static TimeWindowIndex build(List<Transaction> txns, int bucketSeconds) {
        if (txns == null || txns.isEmpty()) {
            throw new IllegalArgumentException("transactions must not be empty");
        }
        if (bucketSeconds < MIN_BUCKET_SECONDS || bucketSeconds > MAX_BUCKET_SECONDS) {
            throw new IllegalArgumentException("bucketSeconds must be between " + MIN_BUCKET_SECONDS
                    + " and " + MAX_BUCKET_SECONDS + ", got " + bucketSeconds);
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Transaction t : txns) {
            min = Math.min(min, t.epochSeconds());
            max = Math.max(max, t.epochSeconds());
        }
        long t0 = Math.floorDiv(min, bucketSeconds) * bucketSeconds;
        int bucketCount = checkedBucketCount((max - t0) / bucketSeconds, bucketSeconds);

        long[] leafSum = new long[bucketCount];
        long[] leafCount = new long[bucketCount];
        long[] leafMax = new long[bucketCount];
        for (Transaction t : txns) {
            int bucket = (int) ((t.epochSeconds() - t0) / bucketSeconds);
            leafSum[bucket] += t.amountPaise();
            leafCount[bucket] += 1;
            leafMax[bucket] = Math.max(leafMax[bucket], t.amountPaise());
        }
        return new TimeWindowIndex(bucketSeconds, t0, min, max, bucketCount, leafSum, leafCount,
                leafMax);
    }

    /**
     * Adds one transaction with a segment tree point update, growing the index first if the
     * timestamp is past the last bucket.
     *
     * <p>Time complexity: O(log B), or O(B) when a rebuild is needed, which is O(1) amortized
     * over appends. Space complexity: O(1), or O(B) during a rebuild.
     *
     * @param t the transaction; its amount must be positive
     * @throws ApiException 422 TIMESTAMP_BEFORE_RANGE if the timestamp is before the first bucket,
     *     or 422 TOO_MANY_BUCKETS if growing would exceed {@link #MAX_BUCKETS}
     * @throws IllegalArgumentException if the amount is not positive
     */
    public void add(Transaction t) {
        long ts = t.epochSeconds();
        if (ts < t0) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("timestamp", ts);
            details.put("firstBucketStart", t0);
            throw new ApiException("TIMESTAMP_BEFORE_RANGE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Timestamp is before the first time bucket of this run", details);
        }
        int bucket = checkedBucketCount((ts - t0) / bucketSeconds, bucketSeconds) - 1;
        if (bucket >= leafSum.length) {
            grow(bucket);
        }
        tree.add(bucket, t.amountPaise());
        leafSum[bucket] += t.amountPaise();
        leafCount[bucket] += 1;
        leafMax[bucket] = Math.max(leafMax[bucket], t.amountPaise());
        dataStart = Math.min(dataStart, ts);
        dataEnd = Math.max(dataEnd, ts);
        bucketCount = Math.max(bucketCount, bucket + 1);
    }

    /**
     * Returns the sum, count and maximum of the amounts in every bucket that touches the window
     * from fromEpoch to toEpoch, both inclusive. A window that reaches outside the data range is
     * clamped to it first.
     *
     * <p>Time complexity: O(log B). Space complexity: O(1).
     *
     * @param fromEpoch the window start, in epoch seconds
     * @param toEpoch the window end, in epoch seconds
     * @return the stats with the effective bounds and whether they were clamped
     * @throws ApiException 400 INVALID_WINDOW if fromEpoch is after toEpoch, or 400
     *     WINDOW_OUT_OF_RANGE if the window lies entirely outside the data range
     */
    public WindowResult query(long fromEpoch, long toEpoch) {
        if (fromEpoch > toEpoch) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("from", fromEpoch);
            details.put("to", toEpoch);
            throw new ApiException("INVALID_WINDOW", HttpStatus.BAD_REQUEST,
                    "Window start is after window end", details);
        }
        if (toEpoch < dataStart || fromEpoch > dataEnd) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requestedFrom", fromEpoch);
            details.put("requestedTo", toEpoch);
            details.put("dataStart", dataStart);
            details.put("dataEnd", dataEnd);
            throw new ApiException("WINDOW_OUT_OF_RANGE", HttpStatus.BAD_REQUEST,
                    "Window lies entirely outside the data range", details);
        }
        long effectiveFrom = Math.max(fromEpoch, dataStart);
        long effectiveTo = Math.min(toEpoch, dataEnd);
        boolean clamped = effectiveFrom != fromEpoch || effectiveTo != toEpoch;
        return new WindowResult(tree.query(bucketIndex(effectiveFrom), bucketIndex(effectiveTo)), effectiveFrom,
                effectiveTo, clamped);
    }

    /**
     * Splits the window from fromEpoch to toEpoch into consecutive windows and answers each with one
     * segment tree query. The window is first clamped and checked exactly as {@link #query} does.
     *
     * <p>The split follows bucket boundaries, so every bucket in the window belongs to exactly one
     * part and the counts of the parts add up to the count of the whole window. When the window
     * covers fewer buckets than requested, one part per bucket is returned.
     *
     * <p>Time complexity: O(p log B) for p parts. Space complexity: O(p).
     *
     * @param fromEpoch the window start, in epoch seconds
     * @param toEpoch the window end, in epoch seconds
     * @param points the number of parts wanted, at least 1
     * @return the parts in time order, each with its own bounds and the clamped flag of the whole
     *     window
     * @throws IllegalArgumentException if points is less than 1
     * @throws ApiException 400 INVALID_WINDOW or 400 WINDOW_OUT_OF_RANGE, as for {@link #query}
     */
    public List<WindowResult> series(long fromEpoch, long toEpoch, int points) {
        if (points < 1) {
            throw new IllegalArgumentException("points must be at least 1, got " + points);
        }
        WindowResult whole = query(fromEpoch, toEpoch);
        int firstBucket = bucketIndex(whole.effectiveFrom());
        int buckets = bucketIndex(whole.effectiveTo()) - firstBucket + 1;
        int parts = Math.min(points, buckets);

        List<WindowResult> series = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            int lo = firstBucket + (int) ((long) i * buckets / parts);
            int hi = firstBucket + (int) ((long) (i + 1) * buckets / parts) - 1;
            long from = Math.max(t0 + (long) lo * bucketSeconds, whole.effectiveFrom());
            long to = Math.min(t0 + (long) (hi + 1) * bucketSeconds - 1, whole.effectiveTo());
            series.add(new WindowResult(tree.query(lo, hi), from, to, whole.clamped()));
        }
        return series;
    }

    /**
     * Returns the earliest transaction timestamp in the index.
     *
     * @return the data start, in epoch seconds
     */
    public long dataStart() {
        return dataStart;
    }

    /**
     * Returns the latest transaction timestamp in the index.
     *
     * @return the data end, in epoch seconds
     */
    public long dataEnd() {
        return dataEnd;
    }

    /**
     * Returns the bucket width.
     *
     * @return the bucket width, in seconds
     */
    public int bucketSeconds() {
        return bucketSeconds;
    }

    /**
     * Returns the number of buckets from the first bucket to the one holding the latest
     * timestamp. Spare capacity added by growth is not counted.
     *
     * @return the bucket count
     */
    public int bucketCount() {
        return bucketCount;
    }

    private void grow(int bucket) {
        long capacity = leafSum.length;
        while (capacity <= bucket) {
            capacity *= 2;
        }
        int newCapacity = (int) Math.min(capacity, MAX_BUCKETS);
        leafSum = Arrays.copyOf(leafSum, newCapacity);
        leafCount = Arrays.copyOf(leafCount, newCapacity);
        leafMax = Arrays.copyOf(leafMax, newCapacity);
        tree = SegmentTree.build(leafSum, leafCount, leafMax);
    }

    /**
     * Returns the index of the bucket that holds a timestamp. The timestamp need not lie inside the data
     * range, so the result may be negative or past the last bucket.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param epochSeconds the timestamp, in epoch seconds
     * @return the bucket index
     */
    public int bucketIndex(long epochSeconds) {
        return (int) ((epochSeconds - t0) / bucketSeconds);
    }

    private static int checkedBucketCount(long lastBucket, int bucketSeconds) {
        long count = lastBucket + 1;
        if (count > MAX_BUCKETS) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("bucketCount", count);
            details.put("maxBuckets", MAX_BUCKETS);
            details.put("bucketSeconds", bucketSeconds);
            throw new ApiException("TOO_MANY_BUCKETS", HttpStatus.UNPROCESSABLE_ENTITY,
                    "The time range needs too many buckets; use a wider bucket", details);
        }
        return (int) count;
    }
}
