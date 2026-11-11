package com.txnscreen.service;

import java.util.HashMap;
import java.util.Map;

/**
 * The exact set of transaction IDs seen so far in a run, each mapped to the file line of its first
 * occurrence.
 */
public final class SeenTransactionStore implements AuthoritativeStore {

    private final Map<String, Integer> firstLines = new HashMap<>();
    private long lookupCount;

    @Override
    public boolean containsExact(String transactionId) {
        lookupCount++;
        return firstLines.containsKey(transactionId);
    }

    @Override
    public long lookupCount() {
        return lookupCount;
    }

    /**
     * Records the line of a transaction ID's first occurrence. A later call for the same ID keeps the
     * first line.
     *
     * @param transactionId the transaction ID
     * @param lineNumber the 1-based file line it appears on
     */
    public void record(String transactionId, int lineNumber) {
        firstLines.putIfAbsent(transactionId, lineNumber);
    }

    /**
     * Returns the line a transaction ID first appeared on, without counting as a lookup.
     *
     * @param transactionId a recorded transaction ID
     * @return the first line number
     * @throws IllegalArgumentException if the ID was never recorded
     */
    public int firstLine(String transactionId) {
        Integer line = firstLines.get(transactionId);
        if (line == null) {
            throw new IllegalArgumentException("Transaction ID was never recorded: " + transactionId);
        }
        return line;
    }
}
