package com.txnscreen.algo.bloom;

/**
 * A Bloom filter over string keys. Each key is hashed once with MurmurHash3, and its k bit positions are
 * derived by Kirsch-Mitzenmacher double hashing: (h1 + i * h2) mod m for i = 0 to k - 1.
 *
 * <p>Bits are never cleared, so there are no false negatives: every added key is reported as possibly
 * present. A positive answer only means "possibly present" and must be confirmed against an exact store.
 *
 * <p>Space complexity: O(m) bits, stored in ceil(m / 64) longs. Not thread safe.
 */
public final class BloomFilter {

    /** The largest allowed bit count: 67,108,864 bits, which is 8 MB. */
    public static final long MAX_BITS = 67_108_864L;

    private static final int WORD_INDEX_SHIFT = 6;

    private final long bitSize;
    private final int hashCount;
    private final long seed;
    private final long[] words;
    private long insertedCount;

    /**
     * Creates an empty filter.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(m) bits.
     *
     * @param mBits the number of bits m, from 1 to {@link #MAX_BITS}
     * @param k the number of hash functions, from 1 to 30
     * @param seed the MurmurHash3 seed, from 0 to {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if mBits or k is out of range
     */
    public BloomFilter(long mBits, int k, long seed) {
        BloomMath.requireBits(mBits);
        if (mBits > MAX_BITS) {
            throw new IllegalArgumentException("Bit count m must be at most " + MAX_BITS + " (8 MB), was " + mBits);
        }
        BloomMath.requireHashes(k);
        this.bitSize = mBits;
        this.hashCount = k;
        this.seed = seed;
        this.words = new long[(int) ((mBits + Long.SIZE - 1) / Long.SIZE)];
    }

    /**
     * Creates an empty filter sized for n keys at false positive rate p, using
     * {@link BloomMath#optimalBits} and {@link BloomMath#optimalHashes}.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(m) bits.
     *
     * @param n the number of keys the filter is designed for, at least 1
     * @param p the target false positive rate, strictly between 0 and 1
     * @param seed the MurmurHash3 seed, from 0 to {@link Integer#MAX_VALUE}
     * @return the new filter
     * @throws IllegalArgumentException if n or p is out of range, or the resulting m or k is too large
     */
    public static BloomFilter forCapacity(long n, double p, long seed) {
        long m = BloomMath.optimalBits(n, p);
        return new BloomFilter(m, BloomMath.optimalHashes(m, n), seed);
    }

    /**
     * Adds a key by setting its k bits. Every call counts towards {@link #insertedCount}, including repeats.
     *
     * <p>Time complexity: O(k), after one O(len) hash of the key. Space complexity: O(1).
     *
     * @param key the key to add
     */
    public void add(String key) {
        Hash128 hash = MurmurHash3.hash128(key, seed);
        for (int i = 0; i < hashCount; i++) {
            long bit = bitIndex(hash, i);
            words[(int) (bit >>> WORD_INDEX_SHIFT)] |= 1L << bit;
        }
        insertedCount++;
    }

    /**
     * Returns false if the key was definitely never added, or true if it is possibly present.
     *
     * <p>Time complexity: O(k), after one O(len) hash of the key. Space complexity: O(1).
     *
     * @param key the key to look up
     * @return true if all k bits of the key are set
     */
    public boolean mightContain(String key) {
        Hash128 hash = MurmurHash3.hash128(key, seed);
        for (int i = 0; i < hashCount; i++) {
            long bit = bitIndex(hash, i);
            if ((words[(int) (bit >>> WORD_INDEX_SHIFT)] & (1L << bit)) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns m, the number of bits.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the bit count
     */
    public long bitSize() {
        return bitSize;
    }

    /**
     * Returns k, the number of hash functions.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the hash count
     */
    public int hashCount() {
        return hashCount;
    }

    /**
     * Returns the number of {@link #add} calls, including repeated keys.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the inserted count
     */
    public long insertedCount() {
        return insertedCount;
    }

    /**
     * Returns the number of bits set to 1.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @return the set bit count
     */
    public long bitsSet() {
        long count = 0;
        for (long word : words) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * Returns the fraction of bits set to 1.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @return bitsSet / m, from 0 to 1
     */
    public double fillRatio() {
        return (double) bitsSet() / bitSize;
    }

    /**
     * Returns the size of the bit array in bytes.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the number of words times 8
     */
    public long memoryBytes() {
        return (long) words.length * Long.BYTES;
    }

    /**
     * Returns the expected false positive rate for the current {@link #insertedCount}, or 0 when empty.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @return the theoretical false positive rate, from 0 to 1
     */
    public double theoreticalFpr() {
        return insertedCount == 0 ? 0 : BloomMath.theoreticalFpr(bitSize, insertedCount, hashCount);
    }

    /**
     * Returns the false positive rate implied by the current fill ratio.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @return the estimated false positive rate, from 0 to 1
     */
    public double estimatedFpr() {
        return BloomMath.estimatedFprFromFill(fillRatio(), hashCount);
    }

    /**
     * Returns true if the filter holds more keys than it was designed for, or its estimated false positive
     * rate is more than twice the target.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @param designCapacity the number of keys the filter was sized for, at least 1
     * @param targetFpr the false positive rate the filter was sized for, strictly between 0 and 1
     * @return true if the filter is saturated
     * @throws IllegalArgumentException if designCapacity or targetFpr is out of range
     */
    public boolean isSaturated(long designCapacity, double targetFpr) {
        BloomMath.requireKeys(designCapacity);
        BloomMath.requireProbability(targetFpr);
        return insertedCount > designCapacity || estimatedFpr() > 2 * targetFpr;
    }

    private long bitIndex(Hash128 hash, int i) {
        return Math.floorMod(hash.h1() + i * hash.h2(), bitSize);
    }
}
