package com.txnscreen.service;

/**
 * A snapshot of one Bloom filter and its exact store after a screening run.
 *
 * @param bitSize the number of bits m
 * @param hashCount the number of hash functions k
 * @param memoryBytes the size of the bit array in bytes
 * @param inserted the number of keys added to the filter
 * @param fillRatio the fraction of bits set
 * @param checks the number of filter lookups
 * @param bloomPositives the lookups the filter answered "possibly present"
 * @param confirmedHits the positives the exact store confirmed
 * @param falsePositives the positives the exact store rejected
 * @param measuredFpr falsePositives / (checks - confirmedHits), or 0 when that denominator is 0
 * @param theoreticalFpr the expected false positive rate for the inserted count
 * @param saturated true if the filter holds more keys than designed for, or its estimated rate is over twice
 *        the target
 * @param suggestedBits the bit count for the inserted count at the target rate, or null when not saturated
 * @param authoritativeLookups the number of exact store lookups
 * @param lookupsAvoided the lookups the filter ruled out, so the exact store was never asked
 */
public record FilterStats(
        long bitSize,
        int hashCount,
        long memoryBytes,
        long inserted,
        double fillRatio,
        long checks,
        long bloomPositives,
        long confirmedHits,
        long falsePositives,
        double measuredFpr,
        double theoreticalFpr,
        boolean saturated,
        Long suggestedBits,
        long authoritativeLookups,
        long lookupsAvoided) {
}
