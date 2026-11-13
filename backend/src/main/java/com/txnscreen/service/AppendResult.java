package com.txnscreen.service;

import java.util.List;

import com.txnscreen.algo.segtree.NodeStats;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.Transaction;

/**
 * The outcome of appending one live transaction to a run.
 *
 * @param transaction the accepted transaction, with the line number it was given
 * @param flags the flags it raised, which may be empty
 * @param bucketIndex the index of the time bucket it fell in
 * @param bucketStats the totals of that bucket after the update
 * @param screenMicros how long screening and the threshold check took, in microseconds
 * @param updateMicros how long the segment tree point update took, in microseconds
 * @param thresholdNote a sentence saying the threshold was not recomputed
 */
public record AppendResult(
        Transaction transaction,
        List<Flag> flags,
        int bucketIndex,
        NodeStats bucketStats,
        long screenMicros,
        long updateMicros,
        String thresholdNote) {
}
