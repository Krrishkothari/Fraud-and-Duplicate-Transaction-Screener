package com.txnscreen.api;

import com.txnscreen.ingest.CsvTransactionParser;
import com.txnscreen.service.RunConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of POST /api/runs/sample. A null component other than size takes its default.
 *
 * @param size the number of rows to generate, from 1 to 200,000
 * @param seed the generator seed, default 42
 * @param duplicateRate the share of rows that repeat an earlier transaction ID, from 0 to 0.2, default 0.02
 * @param blacklistRate the share of accounts that are blacklisted, from 0 to 0.2, default 0.05
 * @param config the run settings, default {@link RunConfig#defaults()}
 */
public record SampleRunRequest(
        @NotNull @Min(1) @Max(CsvTransactionParser.MAX_DATA_ROWS) Integer size,
        Long seed,
        @DecimalMin("0") @DecimalMax("0.2") Double duplicateRate,
        @DecimalMin("0") @DecimalMax("0.2") Double blacklistRate,
        @Valid RunConfig config) {

    public static final long DEFAULT_SEED = 42;
    public static final double DEFAULT_DUPLICATE_RATE = 0.02;
    public static final double DEFAULT_BLACKLIST_RATE = 0.05;

    public SampleRunRequest {
        seed = seed == null ? DEFAULT_SEED : seed;
        duplicateRate = duplicateRate == null ? DEFAULT_DUPLICATE_RATE : duplicateRate;
        blacklistRate = blacklistRate == null ? DEFAULT_BLACKLIST_RATE : blacklistRate;
        config = config == null ? RunConfig.defaults() : config;
    }
}
