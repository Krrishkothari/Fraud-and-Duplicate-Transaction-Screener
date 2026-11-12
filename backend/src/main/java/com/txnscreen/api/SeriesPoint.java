package com.txnscreen.api;

import com.txnscreen.algo.segtree.WindowResult;

/**
 * One window of a chart series. Times are epoch seconds and amounts are paise.
 *
 * @param from the window start
 * @param to the window end
 * @param sumPaise the total amount in the window
 * @param count the number of transactions in the window
 * @param maxPaise the largest single amount in the window, or 0 when it is empty
 */
public record SeriesPoint(long from, long to, long sumPaise, long count, long maxPaise) {

    static SeriesPoint of(WindowResult result) {
        return new SeriesPoint(result.effectiveFrom(), result.effectiveTo(), result.stats().sum(),
                result.stats().count(), result.stats().max());
    }
}
