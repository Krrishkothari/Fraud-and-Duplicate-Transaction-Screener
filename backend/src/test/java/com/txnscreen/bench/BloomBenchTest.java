package com.txnscreen.bench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.txnscreen.algo.bloom.BloomFilter;
import com.txnscreen.algo.bloom.BloomMath;

class BloomBenchTest {

    private static final int SIZE = 10_000;
    private static final long SEED = 42;

    private static List<Measurement> measurements;

    @BeforeAll
    static void runOnce() {
        measurements = new BloomBench().run(SIZE, new BenchConfig(1, 0, SEED));
    }

    // Proves both variants report every metric once, labelled with the benchmark name, size and a unit.
    @Test
    void measuresEveryMetricForBothVariants() {
        Map<String, Set<String>> metricsByVariant = measurements.stream().collect(Collectors.groupingBy(
                Measurement::variant, Collectors.mapping(Measurement::metric, Collectors.toSet())));

        assertEquals(Set.of("bits", "hashes", "memory", "insertTime", "queryTimePresent", "queryTimeAbsent",
                "measuredFpr", "theoreticalFpr", "falseNegatives"), metricsByVariant.get("bloom"));
        assertEquals(Set.of("memoryIncludingKeys", "memoryExcludingKeys", "insertTime", "queryTimePresent",
                "queryTimeAbsent"), metricsByVariant.get("hashset"));
        assertEquals(14, measurements.size());
        for (Measurement m : measurements) {
            assertAll(m.variant() + " " + m.metric(),
                    () -> assertEquals("membership", m.benchmark()),
                    () -> assertEquals(SIZE, m.size()),
                    () -> assertTrue(m.median() >= 0),
                    () -> assertTrue(m.min() <= m.median() && m.median() <= m.max()),
                    () -> assertFalse(m.unit().isEmpty()));
        }
    }

    // Proves the filter is sized optimally for p = 0.01, has no false negatives and lands near its target rate.
    @Test
    void bloomFilterMeetsItsSizingAndTarget() {
        long expectedBits = BloomMath.optimalBits(SIZE, BloomBench.TARGET_FPR);

        assertAll(
                () -> assertEquals(expectedBits, value("bloom", "bits")),
                () -> assertEquals(BloomMath.optimalHashes(expectedBits, SIZE), value("bloom", "hashes")),
                () -> assertEquals(0, value("bloom", "falseNegatives")),
                () -> assertEquals(BloomBench.TARGET_FPR, value("bloom", "theoreticalFpr"), 0.001),
                () -> assertTrue(value("bloom", "measuredFpr") > 0.005 && value("bloom", "measuredFpr") < 0.015,
                        "measured FPR " + value("bloom", "measuredFpr")));
    }

    // Proves the filter, which stores no keys, is smaller than the HashSet even before its keys are counted.
    @Test
    void bloomFilterIsSmallerThanHashSet() {
        double bloom = value("bloom", "memory");
        double excludingKeys = value("hashset", "memoryExcludingKeys");
        double includingKeys = value("hashset", "memoryIncludingKeys");

        assertAll(
                () -> assertTrue(bloom >= BloomMath.memoryBytes((long) value("bloom", "bits")), "bloom " + bloom),
                () -> assertTrue(bloom < excludingKeys, bloom + " vs " + excludingKeys),
                () -> assertTrue(excludingKeys < includingKeys, excludingKeys + " vs " + includingKeys),
                () -> assertTrue(includingKeys - excludingKeys >= SIZE * "ACC000000".length(),
                        "keys " + (includingKeys - excludingKeys)));
    }

    // Proves a false negative stops the benchmark instead of being written as a result.
    @Test
    void falseNegativeIsRejected() {
        BloomFilter emptyFilter = BloomFilter.forCapacity(100, BloomBench.TARGET_FPR, SEED);

        assertThrows(IllegalStateException.class,
                () -> BloomBench.requireNoFalseNegatives(emptyFilter, new String[] {"ACC000000", "ACC000001"}));
    }

    private static double value(String variant, String metric) {
        return measurements.stream()
                .filter(m -> m.variant().equals(variant) && m.metric().equals(metric))
                .findFirst()
                .orElseThrow()
                .median();
    }
}
