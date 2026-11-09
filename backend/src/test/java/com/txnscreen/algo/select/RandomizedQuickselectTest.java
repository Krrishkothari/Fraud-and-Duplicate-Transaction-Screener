package com.txnscreen.algo.select;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomizedQuickselectTest {

    private static final long SEED = 42;

    // Proves the result equals sorting and indexing, across many sizes, values and ranks.
    @Test
    void randomArraysMatchSortedIndex() {
        Random random = new Random(SEED);
        for (int trial = 0; trial < 500; trial++) {
            long[] values = randomValues(random, 1 + random.nextInt(5_000), 1_000_000);
            int k = 1 + random.nextInt(values.length);

            assertEquals(sortedCopy(values)[k - 1],
                    RandomizedQuickselect.kthSmallest(values, k, trial),
                    "trial " + trial + ", n " + values.length + ", k " + k);
        }
    }

    // Proves an array of identical values returns that value for every rank.
    @Test
    void allElementsEqualReturnsThatValueForAnyK() {
        long[] values = new long[1_000];
        Arrays.fill(values, 2_500);

        for (int k = 1; k <= values.length; k++) {
            assertEquals(2_500, RandomizedQuickselect.kthSmallest(values, k, SEED));
        }
    }

    // Proves the smallest valid input works: one element, k = 1, and any percentile.
    @Test
    void singleElementArray() {
        long[] values = {7_350};

        assertAll(
                () -> assertEquals(7_350, RandomizedQuickselect.kthSmallest(values, 1, SEED)),
                () -> assertEquals(7_350, RandomizedQuickselect.percentile(values, 0.01, SEED)),
                () -> assertEquals(7_350, RandomizedQuickselect.percentile(values, 100, SEED)));
    }

    // Proves the percentile bounds: p = 100 is the maximum and a tiny p is the minimum.
    @Test
    void percentileBoundsReturnMaximumAndMinimum() {
        long[] values = randomValues(new Random(SEED), 10_000, 1_000_000);
        long[] sorted = sortedCopy(values);

        assertAll(
                () -> assertEquals(sorted[sorted.length - 1],
                        RandomizedQuickselect.percentile(values, 100, SEED)),
                () -> assertEquals(sorted[0],
                        RandomizedQuickselect.percentile(values, 0.0001, SEED)));
    }

    // Proves already sorted and reverse sorted input, the classic bad case for a fixed pivot, is handled.
    @Test
    void sortedAndReverseSortedLargeArrays() {
        int n = 100_000;
        long[] ascending = new long[n];
        long[] descending = new long[n];
        for (int i = 0; i < n; i++) {
            ascending[i] = i + 1;
            descending[i] = n - i;
        }

        for (int k : new int[] {1, 2, n / 2, n - 1, n}) {
            assertEquals(k, RandomizedQuickselect.kthSmallest(ascending, k, SEED));
            assertEquals(k, RandomizedQuickselect.kthSmallest(descending, k, SEED));
        }
    }

    // Proves the three-way partition is correct when values repeat heavily (3 distinct amounts).
    @Test
    void heavyDuplicatesFromThreeAmounts() {
        long[] amounts = {500, 12_000, 99_900};
        Random random = new Random(SEED);
        long[] values = new long[50_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = amounts[random.nextInt(amounts.length)];
        }
        long[] sorted = sortedCopy(values);

        for (int trial = 0; trial < 200; trial++) {
            int k = 1 + random.nextInt(values.length);
            assertEquals(sorted[k - 1], RandomizedQuickselect.kthSmallest(values, k, trial));
        }
    }

    // Proves the caller's array is never reordered or changed.
    @Test
    void inputArrayIsUnchanged() {
        long[] values = randomValues(new Random(SEED), 5_000, 1_000);
        long[] before = values.clone();

        RandomizedQuickselect.kthSmallest(values, 2_500, SEED);
        RandomizedQuickselect.percentile(values, 99, SEED);
        RandomizedQuickselect.selectPercentile(values, 95, SEED);

        assertArrayEquals(before, values);
    }

    // Proves invalid input is rejected instead of returning a wrong value.
    @Test
    void invalidInputThrows() {
        long[] values = {10, 20, 30};

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.kthSmallest(new long[0], 1, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.kthSmallest(null, 1, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.kthSmallest(values, 0, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.kthSmallest(values, 4, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.percentile(values, 0, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.percentile(values, 100.01, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.percentile(values, Double.NaN, SEED)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> RandomizedQuickselect.percentile(new long[0], 50, SEED)));
    }

    // Proves the nearest-rank rule on a hand-checked example: k = ceil(p / 100 * n).
    @Test
    void nearestRankHandExample() {
        long[] values = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

        assertAll(
                () -> assertEquals(100, RandomizedQuickselect.percentile(values, 95, SEED)),
                () -> assertEquals(90, RandomizedQuickselect.percentile(values, 90, SEED)),
                () -> assertEquals(3, RandomizedQuickselect.percentile(new long[] {5, 1, 4, 2, 3}, 60, SEED)));
    }

    // Proves the rank uses exact decimals, so p = 99.9 of 1,000 values is rank 999, not 1,000.
    @Test
    void nearestRankIsExactForDecimalPercentiles() {
        assertAll(
                () -> assertEquals(999, RandomizedQuickselect.nearestRank(99.9, 1_000)),
                () -> assertEquals(990, RandomizedQuickselect.nearestRank(99, 1_000)),
                () -> assertEquals(1, RandomizedQuickselect.nearestRank(0.0001, 1_000)),
                () -> assertEquals(1_000, RandomizedQuickselect.nearestRank(100, 1_000)));
    }

    // Proves the comparison-counting variant returns the same value and a linear-sized count.
    @Test
    void selectReportsValueAndComparisons() {
        long[] values = randomValues(new Random(SEED), 100_000, 1_000_000);
        long expected = sortedCopy(values)[RandomizedQuickselect.nearestRank(99, values.length) - 1];

        SelectResult result = RandomizedQuickselect.selectPercentile(values, 99, SEED);

        assertEquals(expected, result.value());
        assertTrue(result.comparisons() >= values.length,
                "every element is compared at least once");
        assertTrue(result.comparisons() < 20L * values.length,
                "expected linear work, got " + result.comparisons());
    }

    private static long[] randomValues(Random random, int n, int bound) {
        long[] values = new long[n];
        for (int i = 0; i < n; i++) {
            values[i] = 100 + random.nextInt(bound);
        }
        return values;
    }

    private static long[] sortedCopy(long[] values) {
        long[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }
}
