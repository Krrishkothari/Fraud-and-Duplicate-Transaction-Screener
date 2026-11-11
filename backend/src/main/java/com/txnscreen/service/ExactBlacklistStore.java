package com.txnscreen.service;

import java.util.HashSet;
import java.util.Set;

/**
 * The exact blacklist of account IDs, copied so later changes to the source set have no effect.
 */
public final class ExactBlacklistStore implements AuthoritativeStore {

    private final Set<String> accountIds;
    private long lookupCount;

    public ExactBlacklistStore(Set<String> accountIds) {
        this.accountIds = new HashSet<>(accountIds);
    }

    @Override
    public boolean containsExact(String accountId) {
        lookupCount++;
        return accountIds.contains(accountId);
    }

    @Override
    public long lookupCount() {
        return lookupCount;
    }

    /**
     * Returns the number of distinct blacklisted account IDs.
     *
     * @return the blacklist size
     */
    public int size() {
        return accountIds.size();
    }
}
