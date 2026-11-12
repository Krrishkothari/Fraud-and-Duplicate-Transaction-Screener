package com.txnscreen.api;

import com.txnscreen.service.ThresholdResult;
import com.txnscreen.service.WindowThreshold;

/**
 * A percentile threshold over a run's amounts, for the whole run or one window. Times are epoch seconds and
 * amounts are paise.
 *
 * @param percentile the percentile asked for
 * @param valuePaise the percentile amount, by the nearest-rank method
 * @param n the number of amounts considered
 * @param countAbove the number of amounts strictly greater than the value
 * @param comparisons the element comparisons Quickselect made
 * @param algoMicros the Quickselect time, in microseconds
 * @param effectiveFrom the window start used, after clamping to the data range
 * @param effectiveTo the window end used, after clamping to the data range
 * @param clamped true when the requested window was narrowed to the data range
 */
public record ThresholdResponse(
        double percentile,
        long valuePaise,
        int n,
        long countAbove,
        long comparisons,
        long algoMicros,
        long effectiveFrom,
        long effectiveTo,
        boolean clamped) {

    static ThresholdResponse of(WindowThreshold windowThreshold) {
        ThresholdResult t = windowThreshold.threshold();
        return new ThresholdResponse(t.percentile(), t.valuePaise(), t.n(), t.countAbove(), t.comparisons(),
                t.algoMicros(), windowThreshold.effectiveFrom(), windowThreshold.effectiveTo(),
                windowThreshold.clamped());
    }
}
