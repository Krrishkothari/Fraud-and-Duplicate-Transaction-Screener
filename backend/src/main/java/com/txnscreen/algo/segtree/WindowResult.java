package com.txnscreen.algo.segtree;

/**
 * The answer to one time-window query.
 *
 * @param stats sum, count and maximum of the amounts in the window
 * @param effectiveFrom the first second actually queried, after clamping to the data range
 * @param effectiveTo the last second actually queried, after clamping to the data range
 * @param clamped true when the requested window reached outside the data range and was narrowed
 */
public record WindowResult(NodeStats stats, long effectiveFrom, long effectiveTo, boolean clamped) {
}
