package com.txnscreen.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BenchmarkRunnerTest {

    /** A stand-in benchmark that times a small sum, so the runner can be checked without a real one. */
    private static final Benchmark SELF_CHECK = new Benchmark() {
        @Override
        public String name() {
            return "self-check";
        }

        @Override
        public List<Measurement> run(int size, BenchConfig cfg) {
            Timer.Timing timing = Timer.time(cfg, () -> {
                long sum = 0;
                for (int i = 0; i < size; i++) {
                    sum += i;
                }
                return sum;
            });
            return List.of(timing.toMeasurement(name(), "loop", size, "time", "ns/item", size),
                    Measurement.single(name(), "loop", size, "items", "count", size));
        }
    };

    // Proves the runner writes valid JSON with the environment block, the settings and every measurement.
    @Test
    void writesResultsWithEnvironmentBlock(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("nested").resolve("results.json");
        BenchmarkRunner.Options options = BenchmarkRunner.Options.parse(new String[] {
                "--sizes", "100,1000", "--repeats", "3", "--warmup", "1", "--machine", "Test CPU, 8 GB",
                "--commit", "abc1234", "--out", out.toString()});

        BenchmarkRunner.write(BenchmarkRunner.run(options, List.of(SELF_CHECK)), out);

        JsonNode root = JsonMapper.builder().build().readTree(out.toFile());
        JsonNode environment = root.get("environment");
        assertEquals(System.getProperty("java.version"), environment.get("javaVersion").asString());
        assertEquals("Test CPU, 8 GB", environment.get("machine").asString());
        assertEquals("abc1234", environment.get("commit").asString());
        assertTrue(environment.get("availableProcessors").asInt() >= 1);
        assertFalse(environment.get("timestamp").asString().isEmpty());
        assertEquals(3, root.get("settings").get("repeats").asInt());
        assertEquals(4, root.get("measurements").size());
        JsonNode first = root.get("measurements").get(0);
        assertEquals("self-check", first.get("benchmark").asString());
        assertEquals(100, first.get("size").asInt());
        assertEquals(3, first.get("repeats").asInt());
    }

    // Proves bad options are rejected with a message instead of running with wrong settings.
    @Test
    void invalidOptionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.Options.parse(new String[] {"--size", "100"}));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.Options.parse(new String[] {"--sizes"}));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.Options.parse(new String[] {"--sizes", "100,abc"}));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.Options.parse(new String[] {"--sizes", "0"}));
        assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.Options.parse(new String[] {"--repeats", "0"}));
    }

    // Proves the median is the middle time for an odd count and the mean of the two middle times for an even one.
    @Test
    void timingMedianMinAndMax() {
        Timer.Timing odd = Timer.Timing.of(new long[] {50, 10, 30});
        Timer.Timing even = Timer.Timing.of(new long[] {40, 10, 30, 20});

        assertEquals(30, odd.medianNanos());
        assertEquals(10, odd.minNanos());
        assertEquals(50, odd.maxNanos());
        assertEquals(25, even.medianNanos());
        assertEquals(1.5, Timer.Timing.of(new long[] {1_000_000, 2_000_000})
                .toMeasurement("b", "v", 1, "time", "ms", 1_000_000).median());
    }
}
