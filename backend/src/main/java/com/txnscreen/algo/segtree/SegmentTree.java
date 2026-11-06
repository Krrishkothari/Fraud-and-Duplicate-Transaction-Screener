package com.txnscreen.algo.segtree;

/**
 * Iterative segment tree over positive paise amounts, storing the sum, count and maximum of
 * every node.
 *
 * <p>The tree is a perfect binary tree held in three parallel arrays. Node 1 is the root, the
 * children of node i are 2i and 2i + 1, and leaves start at index capacity, where capacity is
 * the size rounded up to a power of two. Every internal node equals the merge of its children.
 *
 * <p>Space complexity: O(n), since each of the three arrays holds 2 * capacity values and
 * capacity is less than 2n.
 */
public final class SegmentTree {

    /** Largest size whose node arrays (2 * capacity) still fit in an int length. */
    private static final int MAX_SIZE = 1 << 29;

    private final int size;
    private final int capacity;
    private final long[] sum;
    private final long[] count;
    private final long[] max;

    /**
     * Creates a tree of empty leaves.
     *
     * <p>Time complexity: O(n). Space complexity: O(n).
     *
     * @param size the number of leaves, from 1 to 2^29
     * @throws IllegalArgumentException if size is outside that range
     */
    public SegmentTree(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_SIZE + ", got " + size);
        }
        this.size = size;
        this.capacity = roundUpToPowerOfTwo(size);
        this.sum = new long[2 * capacity];
        this.count = new long[2 * capacity];
        this.max = new long[2 * capacity];
    }

    /**
     * Builds a tree from existing leaf values, bottom-up, without repeated updates. The input
     * arrays are copied, not kept.
     *
     * <p>Time complexity: O(n). Space complexity: O(n).
     *
     * @param leafSum the sum of each leaf
     * @param leafCount the count of each leaf
     * @param leafMax the maximum of each leaf
     * @return the built tree
     * @throws IllegalArgumentException if an array is null, the arrays differ in length, or the
     *     length is not a valid size
     */
    public static SegmentTree build(long[] leafSum, long[] leafCount, long[] leafMax) {
        if (leafSum == null || leafCount == null || leafMax == null) {
            throw new IllegalArgumentException("leaf arrays must not be null");
        }
        if (leafSum.length != leafCount.length || leafSum.length != leafMax.length) {
            throw new IllegalArgumentException("leaf arrays must have the same length");
        }
        SegmentTree tree = new SegmentTree(leafSum.length);
        System.arraycopy(leafSum, 0, tree.sum, tree.capacity, leafSum.length);
        System.arraycopy(leafCount, 0, tree.count, tree.capacity, leafCount.length);
        System.arraycopy(leafMax, 0, tree.max, tree.capacity, leafMax.length);
        for (int node = tree.capacity - 1; node >= 1; node--) {
            tree.recompute(node);
        }
        return tree;
    }

    /**
     * Records one amount in a leaf, then recomputes every ancestor of that leaf.
     *
     * <p>Time complexity: O(log n). Space complexity: O(1).
     *
     * @param index the leaf index, from 0 to size - 1
     * @param amountPaise the amount in paise, which must be positive
     * @throws IllegalArgumentException if the index is out of range or the amount is not positive
     */
    public void add(int index, long amountPaise) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException(
                    "index must be between 0 and " + (size - 1) + ", got " + index);
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive, got " + amountPaise);
        }
        int leaf = capacity + index;
        sum[leaf] += amountPaise;
        count[leaf] += 1;
        max[leaf] = Math.max(max[leaf], amountPaise);
        for (int node = leaf / 2; node >= 1; node /= 2) {
            recompute(node);
        }
    }

    /**
     * Returns the merged stats of the leaves from l to r, both inclusive.
     *
     * <p>Walks up from both ends of the range. Whenever a boundary node is not fully covered by
     * its parent, that node is merged into the result and the boundary moves inward, so at most
     * two nodes are merged per level.
     *
     * <p>Time complexity: O(log n). Space complexity: O(1).
     *
     * @param l the first leaf index, inclusive
     * @param r the last leaf index, inclusive
     * @return the stats of the range, with count 0, sum 0 and max 0 when it holds no data
     * @throws IllegalArgumentException if l is greater than r, or either is out of range
     */
    public NodeStats query(int l, int r) {
        if (l < 0 || r >= size || l > r) {
            throw new IllegalArgumentException(
                    "range must satisfy 0 <= l <= r < " + size + ", got l=" + l + ", r=" + r);
        }
        NodeStats result = NodeStats.EMPTY;
        int left = capacity + l;
        int right = capacity + r + 1;
        while (left < right) {
            if ((left & 1) == 1) {
                result = NodeStats.merge(result, nodeStats(left));
                left++;
            }
            if ((right & 1) == 1) {
                right--;
                result = NodeStats.merge(result, nodeStats(right));
            }
            left /= 2;
            right /= 2;
        }
        return result;
    }

    /**
     * Returns the number of leaves the tree was created with.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    private void recompute(int node) {
        int leftChild = 2 * node;
        int rightChild = leftChild + 1;
        sum[node] = sum[leftChild] + sum[rightChild];
        count[node] = count[leftChild] + count[rightChild];
        max[node] = Math.max(max[leftChild], max[rightChild]);
    }

    private NodeStats nodeStats(int node) {
        return new NodeStats(sum[node], count[node], max[node]);
    }

    private static int roundUpToPowerOfTwo(int value) {
        int highestBit = Integer.highestOneBit(value);
        return highestBit == value ? value : highestBit * 2;
    }
}
