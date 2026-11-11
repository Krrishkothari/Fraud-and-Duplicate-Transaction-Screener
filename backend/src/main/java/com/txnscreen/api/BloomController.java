package com.txnscreen.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.txnscreen.algo.bloom.BloomFilter;
import com.txnscreen.algo.bloom.BloomMath;

import jakarta.validation.Valid;

/**
 * Plans Bloom filter sizes without building a filter.
 */
@RestController
@RequestMapping("/api/bloom")
public class BloomController {

    /**
     * Returns the bits, hash count, memory and theoretical false positive rate for n keys at rate p. When k is
     * given it replaces the optimal hash count, and the rate reported is the one that k gives.
     *
     * @throws ApiException 400 INVALID_CONFIG if a parameter is out of range or the plan needs more bits than
     *         {@link BloomFilter#MAX_BITS}
     */
    @GetMapping("/plan")
    public BloomPlan plan(@Valid BloomPlanRequest request) {
        long n = request.n();
        double p = request.p();
        long bits = BloomMath.optimalBits(n, p);
        if (bits > BloomFilter.MAX_BITS) {
            throw new ApiException("INVALID_CONFIG", HttpStatus.BAD_REQUEST, "The configuration is invalid.",
                    Map.of("fieldErrors", List.of(new FieldViolation("n", "n = " + n + " at p = " + p + " needs "
                            + bits + " bits, above the cap of " + BloomFilter.MAX_BITS + " bits (8 MB)"))));
        }
        int hashCount = request.k() == null ? BloomMath.optimalHashes(bits, n) : request.k();
        return new BloomPlan(n, p, bits, hashCount, BloomMath.memoryBytes(bits),
                BloomMath.theoreticalFpr(bits, n, hashCount));
    }
}
