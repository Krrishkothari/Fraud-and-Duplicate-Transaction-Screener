package com.txnscreen.baseline;

import com.txnscreen.algo.segtree.NodeStats;
import com.txnscreen.domain.Transaction;
import java.util.List;

/**
 * The range query baseline the segment tree is benchmarked against: every query scans every transaction and
 * accumulates the sum, count and maximum of those with from &lt;= timestamp &lt;= to.
 *
 * <p>It deliberately uses no index, no binary search and no early exit, so each query is O(n) however small
 * the window is. That is the cost the segment tree avoids by answering from precomputed bucket totals.
 *
 * <p>The timestamps and amounts are copied into two plain arrays once, so the scan reads memory in order
 * instead of following a reference to each transaction. This makes the baseline as fast as a linear scan
 * reasonably gets, so the comparison is fair to it.
 *
 * <p>Space complexity: O(n). Not thread safe while the source list is being changed.
 */
public final class LinearRangeScan {

    private final long[] epochSeconds;
    private final long[] amountPaise;

    /**
     * Copies the timestamps and amounts of the given transactions.
     *
     * <p>Time complexity: O(n). Space complexity: O(n).
     *
     * @param txns the transactions, usually sorted by time; the order does not change any result
     * @throws IllegalArgumentException if txns is null
     */
    public LinearRangeScan(List<Transaction> txns) {
        if (txns == null) {
            throw new IllegalArgumentException("transactions must not be null");
        }
        this.epochSeconds = new long[txns.size()];
        this.amountPaise = new long[txns.size()];
        for (int i = 0; i < txns.size(); i++) {
            Transaction t = txns.get(i);
            epochSeconds[i] = t.epochSeconds();
            amountPaise[i] = t.amountPaise();
        }
    }

    /**
     * Returns the sum, count and maximum of the amounts whose timestamp lies from fromEpoch to toEpoch, both
     * inclusive, by checking every transaction.
     *
     * <p>Time complexity: O(n). Space complexity: O(1).
     *
     * @param fromEpoch the window start, in epoch seconds
     * @param toEpoch the window end, in epoch seconds
     * @return the stats of the window, with count 0, sum 0 and max 0 when no transaction falls in it
     * @throws IllegalArgumentException if fromEpoch is after toEpoch
     */
    public NodeStats query(long fromEpoch, long toEpoch) {
        if (fromEpoch > toEpoch) {
            throw new IllegalArgumentException(
                    "fromEpoch must not be after toEpoch, got " + fromEpoch + " > " + toEpoch);
        }
        long sum = 0;
        long count = 0;
        long max = 0;
        for (int i = 0; i < epochSeconds.length; i++) {
            long ts = epochSeconds[i];
            if (ts >= fromEpoch && ts <= toEpoch) {
                sum += amountPaise[i];
                count++;
                max = Math.max(max, amountPaise[i]);
            }
        }
        return new NodeStats(sum, count, max);
    }
}
