package com.txnscreen.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * One screened upload or generated sample, kept in the {@link RunStore} so its results can be paged and
 * analysed. The analytics index and the high amount threshold are attached when the run is created.
 *
 * <p>The analytics index and the screener are not thread safe, so every read or update of them, and every call
 * to {@link #insertTransaction}, must hold {@link #lock()}. The transaction list and the screening result can be
 * read without the lock: the list is copy-on-write and the screening result is replaced as a whole.
 */
public final class Run {

    private final Object lock = new Object();
    private final UUID id;
    private final Instant createdAt;
    private final RunConfig config;
    private final List<Transaction> transactions;
    private final List<RejectedRow> rejected;
    private final long screeningMicros;
    private volatile ScreeningResult screening;
    private int nextLineNumber;
    private volatile TimeWindowIndex analyticsIndex;
    private volatile long analyticsBuildMicros;
    private volatile ThresholdResult threshold;

    /**
     * Creates a run.
     *
     * @param id the run ID
     * @param createdAt when the run was created, which starts its expiry time
     * @param config the run settings
     * @param transactions the valid transactions, sorted by time
     * @param rejected the rejected rows, in file order
     * @param screening the screening outcome
     * @param screeningMicros how long screening took, in microseconds
     */
    public Run(UUID id, Instant createdAt, RunConfig config, List<Transaction> transactions,
            List<RejectedRow> rejected, ScreeningResult screening, long screeningMicros) {
        this.id = id;
        this.createdAt = createdAt;
        this.config = config;
        this.transactions = new CopyOnWriteArrayList<>(transactions);
        this.rejected = List.copyOf(rejected);
        this.screening = screening;
        this.screeningMicros = screeningMicros;
        this.nextLineNumber = lastLineNumber(transactions, rejected) + 1;
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public RunConfig config() {
        return config;
    }

    /**
     * Returns the valid transactions sorted by time, including any appended live. Transactions with equal
     * times keep the order they arrived in.
     *
     * @return the transactions, as a live view that other threads may extend
     */
    public List<Transaction> transactions() {
        return transactions;
    }

    public List<RejectedRow> rejected() {
        return rejected;
    }

    /**
     * Returns the screening outcome as of the latest append.
     *
     * @return the screening result
     */
    public ScreeningResult screening() {
        return screening;
    }

    /**
     * Returns the screener that holds this run's Bloom filters and exact stores, so live transactions are
     * screened exactly like the batch. The caller must hold {@link #lock()}.
     *
     * @return the screener
     */
    public Screener screener() {
        return screening.screener();
    }

    /**
     * Replaces the screening outcome after a live append.
     *
     * @param screening the new screening result
     */
    public void setScreening(ScreeningResult screening) {
        this.screening = screening;
    }

    /**
     * Returns the file line number the next live transaction gets: one after the last line of the original
     * file, or after the last live transaction. The caller must hold {@link #lock()}.
     *
     * @return the next line number
     */
    public int nextLineNumber() {
        return nextLineNumber;
    }

    /**
     * Adds a live transaction at its place in time order, after any existing transactions with the same
     * time, and moves the next line number past it. The caller must hold {@link #lock()}.
     *
     * <p>Time complexity: O(log n) to find the place plus O(n) to copy the list. Space complexity: O(n).
     *
     * @param transaction the transaction, whose line number is {@link #nextLineNumber()}
     */
    public void insertTransaction(Transaction transaction) {
        int low = 0;
        int high = transactions.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (transactions.get(middle).epochSeconds() <= transaction.epochSeconds()) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        transactions.add(low, transaction);
        nextLineNumber = transaction.lineNumber() + 1;
    }

    public long screeningMicros() {
        return screeningMicros;
    }

    /**
     * Returns the time window index over the transactions, or null until it has been built.
     *
     * @return the analytics index, or null
     */
    public TimeWindowIndex analyticsIndex() {
        return analyticsIndex;
    }

    /**
     * Returns how long building the analytics index took, in microseconds.
     *
     * @return the build time, or 0 before the index is attached
     */
    public long analyticsBuildMicros() {
        return analyticsBuildMicros;
    }

    /**
     * Attaches the time window index built over this run's transactions.
     *
     * @param analyticsIndex the index
     * @param buildMicros how long building it took, in microseconds
     */
    public void setAnalytics(TimeWindowIndex analyticsIndex, long buildMicros) {
        this.analyticsIndex = analyticsIndex;
        this.analyticsBuildMicros = buildMicros;
    }

    /**
     * Returns the object to synchronise on while reading or updating the analytics index, so live appends
     * and window queries never interleave.
     *
     * @return the run's lock
     */
    public Object lock() {
        return lock;
    }

    /**
     * Returns the high amount threshold set when the run was created, from the configured percentile of
     * every valid amount. Amounts strictly above its value are flagged HIGH_AMOUNT.
     *
     * @return the threshold, or null before it is attached
     */
    public ThresholdResult threshold() {
        return threshold;
    }

    public void setThreshold(ThresholdResult threshold) {
        this.threshold = threshold;
    }

    private static int lastLineNumber(List<Transaction> transactions, List<RejectedRow> rejected) {
        int last = 1;
        for (Transaction transaction : transactions) {
            last = Math.max(last, transaction.lineNumber());
        }
        for (RejectedRow row : rejected) {
            last = Math.max(last, row.lineNumber());
        }
        return last;
    }
}
