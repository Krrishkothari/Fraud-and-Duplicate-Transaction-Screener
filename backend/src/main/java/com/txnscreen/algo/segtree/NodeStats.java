package com.txnscreen.algo.segtree;

/**
 * Totals for one segment tree node: the sum, count and largest amount in its range.
 *
 * <p>Amounts are positive paise, so a max of 0 means the range holds no data.
 *
 * @param sum total amount in paise
 * @param count number of amounts
 * @param max largest single amount in paise, or 0 when the range is empty
 */
public record NodeStats(long sum, long count, long max) {

    /** The stats of a range with no data, and the identity value for {@link #merge}. */
    public static final NodeStats EMPTY = new NodeStats(0, 0, 0);

    /**
     * Combines the stats of two disjoint ranges.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param a stats of the first range
     * @param b stats of the second range
     * @return stats of both ranges together
     */
    public static NodeStats merge(NodeStats a, NodeStats b) {
        return new NodeStats(a.sum + b.sum, a.count + b.count, Math.max(a.max, b.max));
    }
}
