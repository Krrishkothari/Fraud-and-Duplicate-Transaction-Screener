package com.txnscreen.algo.segtree;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Random;
import org.junit.jupiter.api.Test;

class SegmentTreeTest {

    // Proves the smallest possible tree (one leaf, no internal nodes) stores and returns data.
    @Test
    void sizeOneTreeAddThenQuery() {
        SegmentTree tree = new SegmentTree(1);
        tree.add(0, 500);
        tree.add(0, 300);

        assertEquals(new NodeStats(800, 2, 500), tree.query(0, 0));
    }

    // Proves the root merges every leaf: the full range equals the totals of all leaves.
    @Test
    void fullRangeQueryEqualsTotalsOfAllLeaves() {
        SegmentTree tree = new SegmentTree(5);
        long[] amounts = {1200, 50, 9900, 300, 4100};
        for (int i = 0; i < amounts.length; i++) {
            tree.add(i, amounts[i]);
        }

        assertEquals(new NodeStats(15550, 5, 9900), tree.query(0, 4));
    }

    // Proves a single-leaf range returns exactly that leaf and nothing from its neighbours.
    @Test
    void equalBoundsReturnExactlyThatLeaf() {
        SegmentTree tree = new SegmentTree(8);
        for (int i = 0; i < 8; i++) {
            tree.add(i, (i + 1) * 100L);
        }
        tree.add(3, 50);

        assertEquals(new NodeStats(450, 2, 400), tree.query(3, 3));
        assertEquals(new NodeStats(100, 1, 100), tree.query(0, 0));
        assertEquals(new NodeStats(800, 1, 800), tree.query(7, 7));
    }

    // Proves add and query agree with a brute-force array over many random operations.
    @Test
    void randomizedOperationsMatchBruteForce() {
        int size = 1_000;
        Random random = new Random(42);
        SegmentTree tree = new SegmentTree(size);
        long[] sum = new long[size];
        long[] count = new long[size];
        long[] max = new long[size];

        for (int op = 0; op < 10_000; op++) {
            if (random.nextBoolean()) {
                int index = random.nextInt(size);
                long amount = 1 + random.nextInt(1_000_000);
                tree.add(index, amount);
                sum[index] += amount;
                count[index] += 1;
                max[index] = Math.max(max[index], amount);
            } else {
                int a = random.nextInt(size);
                int b = random.nextInt(size);
                int l = Math.min(a, b);
                int r = Math.max(a, b);
                assertEquals(bruteForce(sum, count, max, l, r), tree.query(l, r),
                        "operation " + op + " query(" + l + ", " + r + ")");
            }
        }
    }

    // Proves bottom-up build produces the same tree as inserting the same data one add at a time.
    @Test
    void buildMatchesRepeatedAdds() {
        int size = 37;
        Random random = new Random(7);
        SegmentTree added = new SegmentTree(size);
        long[] leafSum = new long[size];
        long[] leafCount = new long[size];
        long[] leafMax = new long[size];
        for (int i = 0; i < 200; i++) {
            int index = random.nextInt(size);
            long amount = 1 + random.nextInt(50_000);
            added.add(index, amount);
            leafSum[index] += amount;
            leafCount[index] += 1;
            leafMax[index] = Math.max(leafMax[index], amount);
        }

        SegmentTree built = SegmentTree.build(leafSum, leafCount, leafMax);

        assertEquals(added.size(), built.size());
        for (int l = 0; l < size; l++) {
            for (int r = l; r < size; r++) {
                assertEquals(added.query(l, r), built.query(l, r), "query(" + l + ", " + r + ")");
            }
        }
    }

    // Proves a range of empty buckets returns the empty stats, not data from outside the range.
    @Test
    void rangeOfEmptyBucketsReturnsZeroes() {
        SegmentTree tree = new SegmentTree(10);
        tree.add(0, 700);
        tree.add(9, 900);

        assertEquals(NodeStats.EMPTY, tree.query(1, 8));
        assertEquals(NodeStats.EMPTY, new SegmentTree(4).query(0, 3));
    }

    // Proves sums near 10^15 paise per leaf are added exactly in long arithmetic, without overflow.
    @Test
    void largeSumsDoNotOverflow() {
        long nearQuadrillion = 999_999_999_999_999L;
        int size = 1_000;
        long[] leafSum = new long[size];
        long[] leafCount = new long[size];
        long[] leafMax = new long[size];
        for (int i = 0; i < size; i++) {
            leafSum[i] = nearQuadrillion;
            leafCount[i] = 1;
            leafMax[i] = nearQuadrillion;
        }
        SegmentTree built = SegmentTree.build(leafSum, leafCount, leafMax);

        SegmentTree added = new SegmentTree(4);
        for (int i = 0; i < 4; i++) {
            added.add(i, nearQuadrillion);
            added.add(i, nearQuadrillion);
        }

        assertAll(
                () -> assertEquals(
                        new NodeStats(nearQuadrillion * size, size, nearQuadrillion),
                        built.query(0, size - 1)),
                () -> assertEquals(
                        new NodeStats(nearQuadrillion * 8, 8, nearQuadrillion),
                        added.query(0, 3)));
    }

    // Proves invalid sizes, indexes and ranges are rejected instead of returning wrong totals.
    @Test
    void invalidInputThrows() {
        SegmentTree tree = new SegmentTree(5);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new SegmentTree(0)),
                () -> assertThrows(IllegalArgumentException.class, () -> tree.add(-1, 100)),
                () -> assertThrows(IllegalArgumentException.class, () -> tree.add(5, 100)),
                () -> assertThrows(IllegalArgumentException.class, () -> tree.query(3, 2)),
                () -> assertThrows(IllegalArgumentException.class, () -> tree.query(-1, 2)),
                () -> assertThrows(IllegalArgumentException.class, () -> tree.query(0, 5)));
    }

    private static NodeStats bruteForce(long[] sum, long[] count, long[] max, int l, int r) {
        long totalSum = 0;
        long totalCount = 0;
        long largest = 0;
        for (int i = l; i <= r; i++) {
            totalSum += sum[i];
            totalCount += count[i];
            largest = Math.max(largest, max[i]);
        }
        return new NodeStats(totalSum, totalCount, largest);
    }
}
