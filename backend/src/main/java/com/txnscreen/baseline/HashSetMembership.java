package com.txnscreen.baseline;

import java.util.HashSet;
import java.util.Set;

/**
 * The exact membership baseline the Bloom filter is benchmarked against: a {@link HashSet} of string keys.
 * It stores every key, so it never gives a false positive or a false negative, and its memory grows with the
 * number of keys and their length.
 *
 * <p>Space complexity: O(n) entries plus the n keys themselves. Not thread safe.
 */
public final class HashSetMembership {

    private final Set<String> keys;

    /**
     * Creates an empty set sized so that expectedKeys keys fit without resizing, as the Bloom filter is also
     * sized for its keys up front.
     *
     * <p>Time complexity: O(expectedKeys), to allocate the table. Space complexity: O(expectedKeys).
     *
     * @param expectedKeys the number of keys the set is sized for, at least 0
     * @throws IllegalArgumentException if expectedKeys is negative
     */
    public HashSetMembership(int expectedKeys) {
        this.keys = HashSet.newHashSet(expectedKeys);
    }

    /**
     * Adds a key. Adding a key that is already present has no effect.
     *
     * <p>Time complexity: O(len) expected, for hashing the key. Space complexity: O(1) per new key, plus the
     * key itself.
     *
     * @param key the key to add
     */
    public void add(String key) {
        keys.add(key);
    }

    /**
     * Returns true if the key was added, with no false positives.
     *
     * <p>Time complexity: O(len) expected, for hashing and comparing the key. Space complexity: O(1).
     *
     * @param key the key to look up
     * @return true if the key is present
     */
    public boolean contains(String key) {
        return keys.contains(key);
    }
}
