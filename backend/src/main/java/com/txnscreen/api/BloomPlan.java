package com.txnscreen.api;

/**
 * A Bloom filter sizing plan.
 *
 * @param n the number of keys planned for
 * @param p the target false positive rate
 * @param bits the bit count m needed for n keys at rate p
 * @param hashCount the hash count k, optimal unless one was given
 * @param memoryBytes the size of the bit array in bytes
 * @param theoreticalFpr the expected false positive rate with these m, n and k
 */
public record BloomPlan(long n, double p, long bits, int hashCount, long memoryBytes, double theoreticalFpr) {
}
