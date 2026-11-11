package com.txnscreen.service;

/**
 * An exact set of keys that confirms or rejects a Bloom filter's "possibly present" answer.
 */
public interface AuthoritativeStore {

    /**
     * Returns true if the key is in the store. Every call counts towards {@link #lookupCount}.
     *
     * <p>Time complexity: O(len) expected, for hashing the key. Space complexity: O(1).
     *
     * @param key the key to look up
     * @return true if the key is present
     */
    boolean containsExact(String key);

    /**
     * Returns the number of {@link #containsExact} calls so far.
     *
     * @return the lookup count
     */
    long lookupCount();
}
