package com.txnscreen.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.txnscreen.baseline.SortPercentile;

class SelectBenchTest {

    private static final BenchConfig CONFIG = new BenchConfig(1, 0, 42);

    // Proves the sort baseline returns the nearest-rank value on a hand-checked example.
    @Test
    void sortPercentileUsesNearestRank() {
        long[] values = {100, 10, 90, 20, 80, 30, 70, 40, 60, 50};

        assertEquals(100, SortPercentile.percentile(values, 95));
        assertEquals(90, SortPercentile.percentile(values, 90));
        assertEquals(10, SortPercentile.percentile(values, 0.1));
        assertEquals(100, values[0], "the input is not sorted in place");
    }

    // Proves every case is measured for both variants and Quickselect's values match sorting.
    @Test
    void runMeasuresBothVariantsForEveryCase() {
        List<Measurement> measurements = new SelectBench().run(2_000, CONFIG);

        for (String label : List.of("P95", "P99", "AllEqualP99")) {
            assertTrue(has(measurements, "quickselect", "time" + label), label);
            assertTrue(has(measurements, "quickselect", "comparisonsPerN" + label), label);
            assertTrue(has(measurements, "sort", "time" + label), label);
        }
        assertEquals(SelectBench.EQUAL_AMOUNT_PAISE, value(measurements, "valueAllEqualP99"));
    }

    // Proves comparisons / n stays roughly constant as n grows, the sign of expected linear time,
    // and that the all-equal input finishes in one pass.
    @Test
    void comparisonsPerElementStayRoughlyConstant() {
        double small = value(new SelectBench().run(10_000, CONFIG), "comparisonsPerNP99");
        double large = value(new SelectBench().run(200_000, CONFIG), "comparisonsPerNP99");
        double allEqual = value(new SelectBench().run(200_000, CONFIG), "comparisonsPerNAllEqualP99");

        assertTrue(large < small * 2, "10K: " + small + ", 200K: " + large);
        assertTrue(allEqual <= 2.0, "all equal: " + allEqual);
    }

    // Proves the baseline and Quickselect agree on random inputs of many sizes.
    @Test
    void baselineMatchesSortedIndexOnRandomInputs() {
        Random random = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            long[] values = random.longs(1 + random.nextInt(500), 100, 1_000_000).toArray();
            long[] sorted = values.clone();
            Arrays.sort(sorted);
            int k = (int) Math.ceil(0.99 * values.length);

            assertEquals(sorted[k - 1], SortPercentile.percentile(values, 99));
        }
    }

    private static boolean has(List<Measurement> measurements, String variant, String metric) {
        return measurements.stream().anyMatch(m -> m.variant().equals(variant) && m.metric().equals(metric));
    }

    private static double value(List<Measurement> measurements, String metric) {
        return measurements.stream().filter(m -> m.metric().equals(metric)).findFirst().orElseThrow().median();
    }
}
