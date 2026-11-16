package com.txnscreen.bench;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.baseline.LinearRangeScan;
import com.txnscreen.data.SyntheticDataGenerator;
import com.txnscreen.domain.Transaction;

class RangeBenchTest {

    private static final int SIZE = 10_000;
    private static final long SEED = 42;

    private static List<Measurement> measurements;

    @BeforeAll
    static void runOnce() {
        measurements = new RangeBench().run(SIZE, new BenchConfig(1, 0, SEED));
    }

    // Proves both variants report every metric once, labelled with the benchmark name, size and a unit.
    @Test
    void measuresEveryMetricForBothVariants() {
        Map<String, Set<String>> metricsByVariant = measurements.stream().collect(Collectors.groupingBy(
                Measurement::variant, Collectors.mapping(Measurement::metric, Collectors.toSet())));

        assertEquals(Set.of("bucketCount", "buildTime", "queryTime", "updateTime", "mismatchedWindows"),
                metricsByVariant.get("segtree"));
        assertEquals(Set.of("queryTime"), metricsByVariant.get("linearscan"));
        assertEquals(6, measurements.size());
        for (Measurement m : measurements) {
            assertAll(m.variant() + " " + m.metric(),
                    () -> assertEquals("range", m.benchmark()),
                    () -> assertEquals(SIZE, m.size()),
                    () -> assertTrue(m.median() >= 0),
                    () -> assertTrue(m.min() <= m.median() && m.median() <= m.max()),
                    () -> assertFalse(m.unit().isEmpty()));
        }
    }

    // Proves the recorded bucket count is the one the index builds for the same generated rows.
    @Test
    void recordsTheIndexBucketCount() {
        List<Transaction> rows = SyntheticDataGenerator.generate(SIZE, SEED, 0.02, 0.05).rows();
        int expected = TimeWindowIndex.build(rows, RangeBench.BUCKET_SECONDS).bucketCount();

        assertEquals(expected, value("segtree", "bucketCount"));
        assertEquals(0, value("segtree", "mismatchedWindows"));
    }

    // Proves every window starts on a bucket boundary, ends on the last second of a bucket, and lies in range.
    @Test
    void windowsFollowBucketBoundaries() {
        List<Transaction> rows = SyntheticDataGenerator.generate(SIZE, SEED, 0.02, 0.05).rows();
        TimeWindowIndex index = TimeWindowIndex.build(rows, RangeBench.BUCKET_SECONDS);
        int width = index.bucketSeconds();

        for (RangeBench.Window window : RangeBench.bucketAlignedWindows(index, 1_000, new Random(SEED))) {
            assertAll(window.toString(),
                    () -> assertEquals(0, Math.floorMod(window.from(), width)),
                    () -> assertEquals(width - 1, Math.floorMod(window.to(), width)),
                    () -> assertTrue(window.from() <= window.to()),
                    () -> assertTrue(index.bucketIndex(window.from()) >= 0),
                    () -> assertTrue(index.bucketIndex(window.to()) < index.bucketCount()));
        }
    }

    // Proves a disagreement between the two methods stops the benchmark instead of being written as a result.
    @Test
    void mismatchIsRejected() {
        List<Transaction> rows = SyntheticDataGenerator.generate(100, SEED, 0.0, 0.0).rows();
        TimeWindowIndex index = TimeWindowIndex.build(rows, RangeBench.BUCKET_SECONDS);
        LinearRangeScan scanMissingARow = new LinearRangeScan(rows.subList(1, rows.size()));
        RangeBench.Window[] wholeRange = {new RangeBench.Window(index.dataStart(), index.dataEnd())};

        assertThrows(IllegalStateException.class,
                () -> RangeBench.requireIdenticalResults(index, scanMissingARow, wholeRange));
    }

    private static double value(String variant, String metric) {
        return measurements.stream()
                .filter(m -> m.variant().equals(variant) && m.metric().equals(metric))
                .findFirst()
                .orElseThrow()
                .median();
    }
}
