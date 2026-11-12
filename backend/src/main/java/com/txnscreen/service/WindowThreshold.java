package com.txnscreen.service;

/**
 * A threshold computed over the transactions of one time window.
 *
 * @param threshold the percentile of the window's amounts
 * @param effectiveFrom the window start actually used, after clamping to the data range, in epoch seconds
 * @param effectiveTo the window end actually used, after clamping to the data range, in epoch seconds
 * @param clamped true when the requested window was narrowed to the data range
 */
public record WindowThreshold(ThresholdResult threshold, long effectiveFrom, long effectiveTo, boolean clamped) {
}
