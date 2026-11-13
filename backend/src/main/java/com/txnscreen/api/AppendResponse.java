package com.txnscreen.api;

import java.util.List;

import com.txnscreen.domain.Flag;
import com.txnscreen.domain.Transaction;
import com.txnscreen.service.AppendResult;

/**
 * The response for one appended live transaction. Amounts are paise.
 *
 * @param transaction the accepted transaction, with the line number it was given
 * @param flags the flags it raised, which may be empty
 * @param bucketIndex the index of the time bucket it fell in
 * @param bucketStats the totals of that bucket after the update
 * @param screenMicros how long screening and the threshold check took, in microseconds
 * @param updateMicros how long the segment tree point update took, in microseconds
 * @param thresholdNote a sentence saying the threshold was not recomputed
 */
public record AppendResponse(
        Transaction transaction,
        List<Flag> flags,
        int bucketIndex,
        BucketStats bucketStats,
        long screenMicros,
        long updateMicros,
        String thresholdNote) {

    /**
     * The totals of one time bucket.
     *
     * @param sumPaise the total amount in the bucket
     * @param count the number of transactions in the bucket
     * @param maxPaise the largest single amount in the bucket
     */
    public record BucketStats(long sumPaise, long count, long maxPaise) {
    }

    static AppendResponse of(AppendResult result) {
        return new AppendResponse(result.transaction(), result.flags(), result.bucketIndex(),
                new BucketStats(result.bucketStats().sum(), result.bucketStats().count(),
                        result.bucketStats().max()),
                result.screenMicros(), result.updateMicros(), result.thresholdNote());
    }
}
