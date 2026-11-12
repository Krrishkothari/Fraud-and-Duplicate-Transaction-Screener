package com.txnscreen.api;

import java.util.List;

import com.txnscreen.algo.segtree.WindowResult;
import com.txnscreen.service.Timed;

/**
 * Consecutive windows covering a time range of a run, for a chart. Times are epoch seconds.
 *
 * @param effectiveFrom the start of the first window, after clamping to the data range
 * @param effectiveTo the end of the last window, after clamping to the data range
 * @param clamped true when the requested range was narrowed to the data range
 * @param points the windows in time order; fewer than requested when the range has fewer buckets
 * @param algoMicros the total segment tree query time, in microseconds
 */
public record SeriesResponse(
        long effectiveFrom,
        long effectiveTo,
        boolean clamped,
        List<SeriesPoint> points,
        long algoMicros) {

    static SeriesResponse of(Timed<List<WindowResult>> timed) {
        List<WindowResult> windows = timed.value();
        return new SeriesResponse(windows.getFirst().effectiveFrom(), windows.getLast().effectiveTo(),
                windows.getFirst().clamped(), windows.stream().map(SeriesPoint::of).toList(), timed.algoMicros());
    }
}
