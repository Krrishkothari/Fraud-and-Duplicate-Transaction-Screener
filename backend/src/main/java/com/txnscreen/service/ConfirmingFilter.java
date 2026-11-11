package com.txnscreen.service;

import com.txnscreen.algo.bloom.BloomFilter;
import com.txnscreen.algo.bloom.BloomMath;

/**
 * A Bloom filter whose "possibly present" answers are confirmed against an exact store, counting
 * positives, confirmed hits, false positives and the exact lookups the filter avoided.
 */
final class ConfirmingFilter {

    private final BloomFilter filter;
    private final AuthoritativeStore store;
    private final long designCapacity;
    private final double targetFpr;
    private long checks;
    private long bloomPositives;
    private long confirmedHits;
    private long falsePositives;

    ConfirmingFilter(BloomFilter filter, AuthoritativeStore store, long designCapacity, double targetFpr) {
        this.filter = filter;
        this.store = store;
        this.designCapacity = designCapacity;
        this.targetFpr = targetFpr;
    }

    /**
     * Returns true only if the filter reports the key as possibly present and the exact store confirms it.
     * A filter negative skips the exact store.
     *
     * <p>Time complexity: O(k) after one O(len) hash, plus one expected O(len) store lookup on a positive.
     * Space complexity: O(1).
     */
    boolean containsConfirmed(String key) {
        checks++;
        if (!filter.mightContain(key)) {
            return false;
        }
        bloomPositives++;
        if (store.containsExact(key)) {
            confirmedHits++;
            return true;
        }
        falsePositives++;
        return false;
    }

    /**
     * Adds a key to the filter only. The caller keeps the exact store in step.
     */
    void add(String key) {
        filter.add(key);
    }

    /**
     * Returns a snapshot of the filter and its counters.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     */
    FilterStats stats() {
        long negatives = checks - confirmedHits;
        double measuredFpr = negatives == 0 ? 0 : (double) falsePositives / negatives;
        boolean saturated = filter.isSaturated(designCapacity, targetFpr);
        Long suggestedBits = saturated
                ? BloomMath.optimalBits(Math.max(1, filter.insertedCount()), targetFpr)
                : null;
        return new FilterStats(filter.bitSize(), filter.hashCount(), filter.memoryBytes(), filter.insertedCount(),
                filter.fillRatio(), checks, bloomPositives, confirmedHits, falsePositives, measuredFpr,
                filter.theoreticalFpr(), saturated, suggestedBits, store.lookupCount(), checks - bloomPositives);
    }
}
