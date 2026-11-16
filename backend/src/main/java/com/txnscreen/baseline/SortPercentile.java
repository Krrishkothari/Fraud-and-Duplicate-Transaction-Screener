package com.txnscreen.baseline;

import java.util.Arrays;

import com.txnscreen.algo.select.RandomizedQuickselect;

/**
 * The percentile baseline Quickselect is benchmarked against: copy the values, sort the copy fully with
 * {@link Arrays#sort(long[])}, then read the nearest-rank index. It uses the same rank formula as
 * {@link RandomizedQuickselect}, so both always return the same value.
 *
 * <p>Sorting orders every value, which costs O(n log n), while Quickselect only finds the one value at the
 * rank it needs, in expected O(n).
 */
public final class SortPercentile {

    private SortPercentile() {
    }

    /**
     * Returns the p-th percentile by the nearest-rank method: the k-th smallest value with
     * k = ceil(p / 100 * n).
     *
     * <p>Time complexity: O(n log n). Space complexity: O(n) for the sorted copy.
     *
     * @param values the values; not modified
     * @param p the percentile, in (0, 100]
     * @return the percentile value
     * @throws IllegalArgumentException if values is null or empty, or p is outside (0, 100]
     */
    public static long percentile(long[] values, double p) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be null or empty");
        }
        int rank = RandomizedQuickselect.nearestRank(p, values.length);
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[rank - 1];
    }
}
