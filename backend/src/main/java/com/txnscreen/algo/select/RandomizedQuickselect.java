package com.txnscreen.algo.select;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Randomized Quickselect with a three-way (Dijkstra) partition.
 *
 * <p>Finds the k-th smallest value without sorting. Each round picks a random pivot,
 * splits the range into values less than, equal to and greater than the pivot, and keeps
 * only the part that contains rank k. The equal block means arrays with many repeated
 * amounts still shrink quickly. Every method works on a copy, so the input is never changed.
 */
public final class RandomizedQuickselect {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private RandomizedQuickselect() {
    }

    /**
     * Returns the k-th smallest value.
     *
     * <p>Time complexity: expected O(n), worst case O(n^2) with vanishing probability.
     * Space complexity: O(n) for the copy, O(1) beyond it.
     *
     * @param values the values to select from; not modified
     * @param k the 1-based rank, in [1, n]
     * @param seed the seed for the pivot choices, so results are reproducible
     * @return the k-th smallest value
     * @throws IllegalArgumentException if values is null or empty, or k is outside [1, n]
     */
    public static long kthSmallest(long[] values, int k, long seed) {
        return select(values, k, seed).value();
    }

    /**
     * Returns the k-th smallest value and the number of element comparisons made.
     *
     * <p>Time complexity: expected O(n), worst case O(n^2) with vanishing probability.
     * Space complexity: O(n) for the copy, O(1) beyond it.
     *
     * @param values the values to select from; not modified
     * @param k the 1-based rank, in [1, n]
     * @param seed the seed for the pivot choices, so results are reproducible
     * @return the k-th smallest value with the comparison count
     * @throws IllegalArgumentException if values is null or empty, or k is outside [1, n]
     */
    public static SelectResult select(long[] values, int k, long seed) {
        requireValues(values);
        if (k < 1 || k > values.length) {
            throw new IllegalArgumentException(
                    "k must be between 1 and " + values.length + ", got " + k);
        }
        return selectInCopy(values.clone(), k - 1, new Random(seed));
    }

    /**
     * Returns the p-th percentile by the nearest-rank method: the k-th smallest value
     * with k = ceil(p / 100 * n).
     *
     * <p>Time complexity: expected O(n), worst case O(n^2) with vanishing probability.
     * Space complexity: O(n) for the copy, O(1) beyond it.
     *
     * @param values the values to select from; not modified
     * @param p the percentile, in (0, 100]
     * @param seed the seed for the pivot choices, so results are reproducible
     * @return the percentile value
     * @throws IllegalArgumentException if values is null or empty, or p is outside (0, 100]
     */
    public static long percentile(long[] values, double p, long seed) {
        return selectPercentile(values, p, seed).value();
    }

    /**
     * Returns the p-th percentile by the nearest-rank method, with the number of element
     * comparisons made.
     *
     * <p>Time complexity: expected O(n), worst case O(n^2) with vanishing probability.
     * Space complexity: O(n) for the copy, O(1) beyond it.
     *
     * @param values the values to select from; not modified
     * @param p the percentile, in (0, 100]
     * @param seed the seed for the pivot choices, so results are reproducible
     * @return the percentile value with the comparison count
     * @throws IllegalArgumentException if values is null or empty, or p is outside (0, 100]
     */
    public static SelectResult selectPercentile(long[] values, double p, long seed) {
        requireValues(values);
        return select(values, nearestRank(p, values.length), seed);
    }

    /**
     * Computes the 1-based nearest rank ceil(p * n / 100) with exact decimal arithmetic,
     * so a percentile such as 99.9 never rounds to the wrong rank.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param p the percentile, in (0, 100]
     * @param n the number of values, at least 1
     * @return the rank, clamped to [1, n]
     * @throws IllegalArgumentException if p is outside (0, 100]
     */
    public static int nearestRank(double p, int n) {
        if (!(p > 0 && p <= 100)) {
            throw new IllegalArgumentException("p must be in (0, 100], got " + p);
        }
        int rank = BigDecimal.valueOf(p)
                .multiply(BigDecimal.valueOf(n))
                .divide(HUNDRED, 0, RoundingMode.CEILING)
                .intValueExact();
        return Math.max(1, Math.min(n, rank));
    }

    /**
     * Narrows [lo, hi] around the target index until it lands in a block of values equal
     * to the pivot. Invariant: the value of rank target + 1 always lies inside [lo, hi].
     */
    private static SelectResult selectInCopy(long[] a, int target, Random random) {
        int lo = 0;
        int hi = a.length - 1;
        long comparisons = 0;
        while (true) {
            long pivot = a[lo + random.nextInt(hi - lo + 1)];
            // After the pass: [lo, lt) < pivot, [lt, gt] == pivot, (gt, hi] > pivot.
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                comparisons++;
                if (a[i] < pivot) {
                    swap(a, lt++, i++);
                } else {
                    comparisons++;
                    if (a[i] > pivot) {
                        swap(a, i, gt--);
                    } else {
                        i++;
                    }
                }
            }
            if (target < lt) {
                hi = lt - 1;
            } else if (target > gt) {
                lo = gt + 1;
            } else {
                return new SelectResult(pivot, comparisons);
            }
        }
    }

    private static void requireValues(long[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be null or empty");
        }
    }

    private static void swap(long[] a, int i, int j) {
        long tmp = a[i];
        a[i] = a[j];
        a[j] = tmp;
    }
}
