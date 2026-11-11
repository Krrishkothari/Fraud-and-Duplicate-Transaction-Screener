package com.txnscreen.api;

import com.txnscreen.algo.bloom.BloomMath;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The query parameters of GET /api/bloom/plan.
 *
 * @param n the number of keys to plan for, at least 1
 * @param p the target false positive rate, strictly between 0 and 1
 * @param k the hash count to use instead of the optimal one, from 1 to 30, or null
 */
public record BloomPlanRequest(
        @NotNull @Min(1) Long n,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false) Double p,
        @Min(BloomMath.MIN_HASHES) @Max(BloomMath.MAX_HASHES) Integer k) {
}
