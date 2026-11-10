package com.txnscreen.algo.bloom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BloomMathTest {

    private static final long KEYS = 1_000_000;
    private static final double TARGET_FPR = 0.01;
    private static final long EXPECTED_BITS = 9_585_059;
    private static final int EXPECTED_HASHES = 7;
    private static final double FPR_TOLERANCE = 0.0005;

    // Proves the sizing formulas give the textbook m and k for one million keys at 1 percent.
    @Test
    void sizesOneMillionKeysAtOnePercent() {
        assertEquals(EXPECTED_BITS, BloomMath.optimalBits(KEYS, TARGET_FPR));
        assertEquals(EXPECTED_HASHES, BloomMath.optimalHashes(EXPECTED_BITS, KEYS));
    }

    // Proves the optimal m and k bring the theoretical false positive rate back to the target.
    @Test
    void theoreticalFprMatchesTargetAtOptimalSize() {
        assertEquals(TARGET_FPR, BloomMath.theoreticalFpr(EXPECTED_BITS, KEYS, EXPECTED_HASHES), FPR_TOLERANCE);
    }

    // Proves a rate of 0 or 1 cannot size a filter.
    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void optimalBitsRejectsProbabilityOutsideOpenInterval(double p) {
        assertThrows(IllegalArgumentException.class, () -> BloomMath.optimalBits(KEYS, p));
    }

    // Proves a filter cannot be sized for zero keys.
    @Test
    void optimalBitsRejectsZeroKeys() {
        assertThrows(IllegalArgumentException.class, () -> BloomMath.optimalBits(0, TARGET_FPR));
    }

    // Proves the rate formula needs at least one hash function.
    @Test
    void theoreticalFprRejectsZeroHashes() {
        assertThrows(IllegalArgumentException.class, () -> BloomMath.theoreticalFpr(EXPECTED_BITS, KEYS, 0));
    }
}
