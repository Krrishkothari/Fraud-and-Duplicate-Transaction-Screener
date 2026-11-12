package com.txnscreen.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * One screened upload or generated sample, kept in the {@link RunStore} so its results can be paged and
 * analysed. The analytics index is attached when the run is created; the high amount threshold starts empty
 * and is filled on first use.
 *
 * <p>The analytics index is not thread safe, so every read or update of it must hold {@link #lock()}.
 */
public final class Run {

    private final Object lock = new Object();
    private final UUID id;
    private final Instant createdAt;
    private final RunConfig config;
    private final List<Transaction> transactions;
    private final List<RejectedRow> rejected;
    private final ScreeningResult screening;
    private final long screeningMicros;
    private volatile TimeWindowIndex analyticsIndex;
    private volatile long analyticsBuildMicros;
    private volatile Long thresholdPaise;

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
        this.transactions = List.copyOf(transactions);
        this.rejected = List.copyOf(rejected);
        this.screening = screening;
        this.screeningMicros = screeningMicros;
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

    public List<Transaction> transactions() {
        return transactions;
    }

    public List<RejectedRow> rejected() {
        return rejected;
    }

    public ScreeningResult screening() {
        return screening;
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
     * Returns the high amount threshold in paise, or null until it has been computed.
     *
     * @return the threshold, or null
     */
    public Long thresholdPaise() {
        return thresholdPaise;
    }

    public void setThresholdPaise(Long thresholdPaise) {
        this.thresholdPaise = thresholdPaise;
    }
}
