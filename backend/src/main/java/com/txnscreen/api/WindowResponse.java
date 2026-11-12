package com.txnscreen.api;

import com.txnscreen.algo.segtree.WindowResult;
import com.txnscreen.service.Timed;

/**
 * The totals for one time window of a run. All times are epoch seconds and all amounts are paise.
 *
 * @param from the requested window start
 * @param to the requested window end
 * @param effectiveFrom the start actually queried, after clamping to the data range
 * @param effectiveTo the end actually queried, after clamping to the data range
 * @param clamped true when the requested window was narrowed to the data range
 * @param sumPaise the total amount in the window
 * @param count the number of transactions in the window
 * @param maxPaise the largest single amount in the window, or 0 when it is empty
 * @param algoMicros the segment tree query time, in microseconds
 */
public record WindowResponse(
        long from,
        long to,
        long effectiveFrom,
        long effectiveTo,
        boolean clamped,
        long sumPaise,
        long count,
        long maxPaise,
        long algoMicros) {

    static WindowResponse of(long from, long to, Timed<WindowResult> timed) {
        WindowResult result = timed.value();
        return new WindowResponse(from, to, result.effectiveFrom(), result.effectiveTo(), result.clamped(),
                result.stats().sum(), result.stats().count(), result.stats().max(), timed.algoMicros());
    }
}
