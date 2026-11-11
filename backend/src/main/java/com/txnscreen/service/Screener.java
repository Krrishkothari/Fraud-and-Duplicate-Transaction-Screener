package com.txnscreen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.txnscreen.algo.bloom.BloomFilter;
import com.txnscreen.algo.bloom.BloomMath;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;

/**
 * Screens the transactions of one run for blacklisted accounts and repeated transaction IDs. Each check asks
 * a Bloom filter first, and only a "possibly present" answer goes to the exact store, which decides whether a
 * flag is raised. Not thread safe.
 */
public final class Screener {

    private final ConfirmingFilter blacklist;
    private final ConfirmingFilter seen;
    private final SeenTransactionStore seenStore = new SeenTransactionStore();
    private final int blacklistSize;

    /**
     * Builds and fills the blacklist filter, and builds an empty duplicate filter.
     *
     * <p>Time complexity: O(b + m / 64) for b blacklisted accounts and m total bits. Space complexity: O(b + m)
     * bits.
     *
     * @param blacklistedAccounts the exact blacklist, which may be empty
     * @param expectedInsertions the number of transaction IDs the duplicate filter is sized for, at least 1
     * @param config the target rate, optional hash count and seed
     * @throws IllegalArgumentException if a filter would be larger than {@link BloomFilter#MAX_BITS}
     */
    public Screener(Set<String> blacklistedAccounts, long expectedInsertions, RunConfig config) {
        double targetFpr = config.targetFpr();
        ExactBlacklistStore blacklistStore = new ExactBlacklistStore(blacklistedAccounts);
        blacklistSize = blacklistStore.size();

        long blacklistCapacity = Math.max(1, blacklistSize);
        BloomFilter blacklistFilter = BloomFilter.forCapacity(blacklistCapacity, targetFpr, config.seed());
        blacklistedAccounts.forEach(blacklistFilter::add);
        blacklist = new ConfirmingFilter(blacklistFilter, blacklistStore, blacklistCapacity, targetFpr);

        BloomFilter seenFilter = config.hashCount() == null
                ? BloomFilter.forCapacity(expectedInsertions, targetFpr, config.seed())
                : new BloomFilter(BloomMath.optimalBits(expectedInsertions, targetFpr), config.hashCount(),
                        config.seed());
        seen = new ConfirmingFilter(seenFilter, seenStore, expectedInsertions, targetFpr);
    }

    /**
     * Screens one transaction, in time order, and records its ID for later duplicate checks. The first
     * occurrence of an ID is never flagged; each later one is flagged DUPLICATE against the first line.
     *
     * <p>Time complexity: O(k) after hashing the account and transaction IDs, plus at most one expected
     * O(len) exact lookup per filter positive. Space complexity: O(1) amortised, plus one store entry per new ID.
     *
     * @param transaction the transaction to screen
     * @return the flags raised, BLACKLISTED before DUPLICATE, or an empty list
     */
    public List<Flag> screenOne(Transaction transaction) {
        List<Flag> flags = new ArrayList<>(2);
        String accountId = transaction.accountId();
        String transactionId = transaction.transactionId();

        if (blacklist.containsConfirmed(accountId)) {
            flags.add(new Flag(transactionId, transaction.lineNumber(), FlagType.BLACKLISTED,
                    "Account " + accountId + " is on the blacklist"));
        }

        if (seen.containsConfirmed(transactionId)) {
            flags.add(new Flag(transactionId, transaction.lineNumber(), FlagType.DUPLICATE,
                    "Duplicate of line " + seenStore.firstLine(transactionId)));
        } else {
            seen.add(transactionId);
            seenStore.record(transactionId, transaction.lineNumber());
        }
        return flags;
    }

    /**
     * Returns the number of distinct blacklisted accounts.
     *
     * @return the blacklist size
     */
    public int blacklistSize() {
        return blacklistSize;
    }

    /**
     * Returns a snapshot of the blacklist filter.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @return the blacklist filter stats
     */
    public FilterStats blacklistStats() {
        return blacklist.stats();
    }

    /**
     * Returns a snapshot of the duplicate filter.
     *
     * <p>Time complexity: O(m / 64). Space complexity: O(1).
     *
     * @return the duplicate filter stats
     */
    public FilterStats duplicateStats() {
        return seen.stats();
    }
}
