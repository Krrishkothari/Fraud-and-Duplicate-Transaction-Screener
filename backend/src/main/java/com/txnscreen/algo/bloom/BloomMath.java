package com.txnscreen.algo.bloom;

/**
 * Sizing and false positive rate formulas for a Bloom filter with m bits, n keys and k hash functions.
 */
public final class BloomMath {

    /** The smallest allowed hash count k. */
    public static final int MIN_HASHES = 1;

    /** The largest allowed hash count k. */
    public static final int MAX_HASHES = 30;

    private static final double LN2 = Math.log(2);
    private static final double LN2_SQUARED = LN2 * LN2;

    private BloomMath() {
    }

    /**
     * Returns the number of bits needed to hold n keys at false positive rate p: ceil(-n * ln p / (ln 2)^2).
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param n the number of keys, at least 1
     * @param p the target false positive rate, strictly between 0 and 1
     * @return the bit count m
     * @throws IllegalArgumentException if n or p is out of range
     */
    public static long optimalBits(long n, double p) {
        requireKeys(n);
        requireProbability(p);
        return (long) Math.ceil(-n * Math.log(p) / LN2_SQUARED);
    }

    /**
     * Returns the number of hash functions that minimises the false positive rate: max(1, round(m / n * ln 2)).
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param m the number of bits, at least 1
     * @param n the number of keys, at least 1
     * @return the hash count k, capped at {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if m or n is out of range
     */
    public static int optimalHashes(long m, long n) {
        requireBits(m);
        requireKeys(n);
        long k = Math.max(MIN_HASHES, Math.round((double) m / n * LN2));
        return (int) Math.min(k, Integer.MAX_VALUE);
    }

    /**
     * Returns the expected false positive rate after n insertions: (1 - e^(-k * n / m))^k.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param m the number of bits, at least 1
     * @param n the number of keys inserted, at least 1
     * @param k the number of hash functions, from 1 to 30
     * @return the false positive rate, from 0 to 1
     * @throws IllegalArgumentException if m, n or k is out of range
     */
    public static double theoreticalFpr(long m, long n, int k) {
        requireBits(m);
        requireKeys(n);
        requireHashes(k);
        double setProbability = -Math.expm1(-(double) k * n / m);
        return Math.pow(setProbability, k);
    }

    /**
     * Returns the false positive rate implied by the fraction of bits set: fillRatio^k.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param fillRatio the fraction of bits set, from 0 to 1
     * @param k the number of hash functions, from 1 to 30
     * @return the false positive rate, from 0 to 1
     * @throws IllegalArgumentException if fillRatio or k is out of range
     */
    public static double estimatedFprFromFill(double fillRatio, int k) {
        if (!(fillRatio >= 0 && fillRatio <= 1)) {
            throw new IllegalArgumentException("Fill ratio must be between 0 and 1, was " + fillRatio);
        }
        requireHashes(k);
        return Math.pow(fillRatio, k);
    }

    /**
     * Returns the bytes needed to store m bits as whole 64-bit words: ceil(m / 64) * 8.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param m the number of bits, at least 1
     * @return the size of the bit array in bytes
     * @throws IllegalArgumentException if m is out of range
     */
    public static long memoryBytes(long m) {
        requireBits(m);
        return wordCount(m) * Long.BYTES;
    }

    static long wordCount(long m) {
        return (m + Long.SIZE - 1) / Long.SIZE;
    }

    static void requireKeys(long n) {
        if (n < 1) {
            throw new IllegalArgumentException("Key count n must be at least 1, was " + n);
        }
    }

    static void requireProbability(double p) {
        if (!(p > 0 && p < 1)) {
            throw new IllegalArgumentException("False positive rate p must be strictly between 0 and 1, was " + p);
        }
    }

    static void requireBits(long m) {
        if (m < 1) {
            throw new IllegalArgumentException("Bit count m must be at least 1, was " + m);
        }
    }

    static void requireHashes(int k) {
        if (k < MIN_HASHES || k > MAX_HASHES) {
            throw new IllegalArgumentException(
                    "Hash count k must be from " + MIN_HASHES + " to " + MAX_HASHES + ", was " + k);
        }
    }
}
