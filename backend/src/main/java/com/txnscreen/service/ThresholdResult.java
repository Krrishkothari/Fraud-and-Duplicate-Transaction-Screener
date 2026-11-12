package com.txnscreen.service;

/**
 * A percentile of a set of amounts, found with randomized Quickselect.
 *
 * @param percentile the percentile asked for, in (0, 100]
 * @param valuePaise the percentile amount, by the nearest-rank method
 * @param n the number of amounts considered
 * @param countAbove the number of amounts strictly greater than the value
 * @param comparisons the element comparisons Quickselect made
 * @param algoMicros the Quickselect time, in microseconds
 */
public record ThresholdResult(
        double percentile,
        long valuePaise,
        int n,
        long countAbove,
        long comparisons,
        long algoMicros) {
}
