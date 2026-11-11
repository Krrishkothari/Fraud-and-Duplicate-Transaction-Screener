package com.txnscreen.service;

import com.txnscreen.algo.bloom.BloomMath;
import com.txnscreen.algo.segtree.TimeWindowIndex;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * The settings for one screening run. A null component takes its default, except expectedInsertions and
 * hashCount, which stay null.
 *
 * @param expectedInsertions the number of transaction IDs the duplicate filter is sized for, or null for the
 *        valid row count
 * @param targetFpr the false positive rate both filters are sized for, default 0.01
 * @param hashCount the duplicate filter's hash count, or null for the optimal count
 * @param bucketSeconds the width of a time bucket in seconds, default 60
 * @param percentile the amount percentile used as the high amount threshold, default 99
 * @param seed the hash and random seed, default 42
 */
public record RunConfig(
        @Positive Integer expectedInsertions,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false) Double targetFpr,
        @Min(BloomMath.MIN_HASHES) @Max(BloomMath.MAX_HASHES) Integer hashCount,
        @Min(TimeWindowIndex.MIN_BUCKET_SECONDS) @Max(TimeWindowIndex.MAX_BUCKET_SECONDS) Integer bucketSeconds,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") Double percentile,
        @PositiveOrZero @Max(Integer.MAX_VALUE) Long seed) {

    public static final double DEFAULT_TARGET_FPR = 0.01;
    public static final int DEFAULT_BUCKET_SECONDS = 60;
    public static final double DEFAULT_PERCENTILE = 99;
    public static final long DEFAULT_SEED = 42;

    public RunConfig {
        targetFpr = targetFpr == null ? DEFAULT_TARGET_FPR : targetFpr;
        bucketSeconds = bucketSeconds == null ? DEFAULT_BUCKET_SECONDS : bucketSeconds;
        percentile = percentile == null ? DEFAULT_PERCENTILE : percentile;
        seed = seed == null ? DEFAULT_SEED : seed;
    }

    /**
     * Returns a config with every default.
     *
     * @return the default config
     */
    public static RunConfig defaults() {
        return new RunConfig(null, null, null, null, null, null);
    }
}
