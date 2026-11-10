package com.txnscreen.algo.bloom;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BloomFilterTest {

    private static final long HASH_SEED = 42;
    private static final long RANDOM_SEED = 20_261_110L;
    private static final int CAPACITY = 100_000;
    private static final double TARGET_FPR = 0.01;
    private static final String KEY_PREFIX = "ACC";

    // Proves a filter with no bits set rules out every key.
    @Test
    void emptyFilterContainsNothing() {
        BloomFilter filter = BloomFilter.forCapacity(CAPACITY, TARGET_FPR, HASH_SEED);
        Random random = new Random(RANDOM_SEED);

        for (int i = 0; i < 10_000; i++) {
            String key = Long.toString(random.nextLong(), Character.MAX_RADIX);
            assertFalse(filter.mightContain(key), key);
        }
    }

    // Proves there are no false negatives: every inserted key is reported as possibly present.
    @Test
    void everyInsertedKeyIsFound() {
        BloomFilter filter = filledFilter();

        for (int i = 0; i < CAPACITY; i++) {
            assertTrue(filter.mightContain(KEY_PREFIX + i), KEY_PREFIX + i);
        }
    }

    // Proves the measured false positive rate at design capacity stays close to the 1 percent target.
    @Test
    void measuredFprIsNearTarget() {
        BloomFilter filter = filledFilter();

        int falsePositives = 0;
        for (int i = CAPACITY; i < 2 * CAPACITY; i++) {
            if (filter.mightContain(KEY_PREFIX + i)) {
                falsePositives++;
            }
        }
        double measuredFpr = (double) falsePositives / CAPACITY;

        assertTrue(measuredFpr >= 0.005 && measuredFpr <= 0.015, "measured FPR " + measuredFpr);
    }

    // Proves an overfilled filter reports saturation from its fill ratio alone, since it is not over capacity.
    @Test
    void overfilledFilterIsSaturated() {
        int keyCount = 1_000;
        BloomFilter filter = new BloomFilter(64, 1, HASH_SEED);
        for (int i = 0; i < keyCount; i++) {
            filter.add(KEY_PREFIX + i);
        }

        assertAll(
                () -> assertTrue(filter.isSaturated(keyCount, TARGET_FPR)),
                () -> assertTrue(filter.fillRatio() > 0.99, "fill ratio " + filter.fillRatio()));
    }

    // Proves the empty string and multi-byte UTF-8 keys hash and match like any other key.
    @Test
    void emptyAndNonAsciiKeysWork() {
        BloomFilter filter = BloomFilter.forCapacity(CAPACITY, TARGET_FPR, HASH_SEED);
        String nonAscii = "खाता-42";

        assertAll(
                () -> assertFalse(filter.mightContain("")),
                () -> assertFalse(filter.mightContain(nonAscii)));

        filter.add("");
        filter.add(nonAscii);

        assertAll(
                () -> assertTrue(filter.mightContain("")),
                () -> assertTrue(filter.mightContain(nonAscii)),
                () -> assertEquals(2, filter.insertedCount()));
    }

    // Proves a rate of 0 or 1 cannot size a filter.
    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void forCapacityRejectsProbabilityOutsideOpenInterval(double p) {
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forCapacity(CAPACITY, p, HASH_SEED));
    }

    // Proves a filter cannot be sized for zero keys.
    @Test
    void forCapacityRejectsZeroKeys() {
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forCapacity(0, TARGET_FPR, HASH_SEED));
    }

    // Proves a filter needs at least one hash function.
    @Test
    void constructorRejectsZeroHashes() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(64, 0, HASH_SEED));
    }

    // Proves the bit array cannot grow past the 8 MB cap.
    @Test
    void constructorRejectsBitCountAboveCap() {
        assertThrows(IllegalArgumentException.class,
                () -> new BloomFilter(BloomFilter.MAX_BITS + 1, 1, HASH_SEED));
    }

    private static BloomFilter filledFilter() {
        BloomFilter filter = BloomFilter.forCapacity(CAPACITY, TARGET_FPR, HASH_SEED);
        for (int i = 0; i < CAPACITY; i++) {
            filter.add(KEY_PREFIX + i);
        }
        return filter;
    }
}
