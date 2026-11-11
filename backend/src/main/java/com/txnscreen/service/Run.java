package com.txnscreen.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;

/**
 * One screened upload or generated sample, kept in the {@link RunStore} so its results can be paged and
 * analysed. The analytics index and the high amount threshold start empty and are filled on first use.
 */
public final class Run {

    private final UUID id;
    private final Instant createdAt;
    private final RunConfig config;
    private final List<Transaction> transactions;
    private final List<RejectedRow> rejected;
    private final ScreeningResult screening;
    private final long screeningMicros;
    private volatile TimeWindowIndex analyticsIndex;
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

    public void setAnalyticsIndex(TimeWindowIndex analyticsIndex) {
        this.analyticsIndex = analyticsIndex;
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
