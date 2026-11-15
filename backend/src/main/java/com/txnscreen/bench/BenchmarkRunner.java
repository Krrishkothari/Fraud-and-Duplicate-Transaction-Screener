package com.txnscreen.bench;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs every registered benchmark at every size and writes the results as JSON, with an environment block
 * describing the machine and JVM. Run it from backend/:
 *
 * <pre>
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.txnscreen.bench.BenchmarkRunner \
 *     -Dexec.args="--sizes 10000,100000,1000000 --repeats 7 --warmup 3 --seed 42 \
 *     --machine 'CPU, RAM' --commit $(git rev-parse --short HEAD) --out ../docs/benchmarks/results.json"
 * </pre>
 *
 * <p>exec:java runs inside the Maven JVM, so give it enough heap for 1,000,000 rows first:
 * {@code MAVEN_OPTS=-Xmx3g} (PowerShell: {@code $env:MAVEN_OPTS="-Xmx3g"}). Every option is optional; the
 * defaults are the values above, with machine and commit "unknown".
 */
public final class BenchmarkRunner {

    /** The benchmarks the runner measures, in the order they appear in the results. */
    static final List<Benchmark> BENCHMARKS = List.of(new BloomBench());

    private static final Set<String> OPTIONS =
            Set.of("--sizes", "--repeats", "--warmup", "--seed", "--machine", "--commit", "--out");

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private BenchmarkRunner() {
    }

    public static void main(String[] args) throws IOException {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: BenchmarkRunner [--sizes N,N,...] [--repeats N] [--warmup N] [--seed N] "
                    + "[--machine TEXT] [--commit HASH] [--out FILE]");
            System.exit(2);
            return;
        }
        Results results = run(options, BENCHMARKS);
        write(results, options.out());
        System.out.printf("Wrote %d measurements to %s%n", results.measurements().size(),
                options.out().toAbsolutePath().normalize());
    }

    /**
     * Runs every benchmark at every size, in order, printing progress as it goes.
     *
     * @param options the sizes, settings and machine details
     * @param benchmarks the benchmarks to run
     * @return the environment, settings and every measurement
     */
    static Results run(Options options, List<Benchmark> benchmarks) {
        Environment environment = Environment.current(options.machine(), options.commit());
        List<Measurement> measurements = new ArrayList<>();
        for (Benchmark benchmark : benchmarks) {
            for (int size : options.sizes()) {
                System.out.printf("%s at size %d...%n", benchmark.name(), size);
                measurements.addAll(benchmark.run(size, options.config()));
            }
        }
        Settings settings = new Settings(options.sizes(), options.config().repeats(), options.config().warmup(),
                options.config().seed());
        return new Results(environment, settings, List.copyOf(measurements));
    }

    /**
     * Writes the results as indented JSON, creating the parent folder if needed.
     *
     * @param results the results
     * @param out the file to create or replace
     * @throws IOException if the file cannot be written
     */
    static void write(Results results, Path out) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            JSON.writeValue(writer, results);
        }
    }

    /** The whole results file. */
    record Results(Environment environment, Settings settings, List<Measurement> measurements) {
    }

    /** The settings every benchmark ran with. */
    record Settings(List<Integer> sizes, int repeats, int warmup, long seed) {
    }

    /**
     * The machine and JVM the results were measured on.
     *
     * @param maxMemory the largest heap the JVM may use, in bytes
     * @param timestamp when the run started, as an ISO-8601 instant
     */
    record Environment(String javaVersion, String javaVmName, String osName, String osArch,
            int availableProcessors, long maxMemory, String machine, String commit, String timestamp) {

        static Environment current(String machine, String commit) {
            Runtime runtime = Runtime.getRuntime();
            return new Environment(System.getProperty("java.version"), System.getProperty("java.vm.name"),
                    System.getProperty("os.name"), System.getProperty("os.arch"), runtime.availableProcessors(),
                    runtime.maxMemory(), machine, commit, Instant.now().toString());
        }
    }

    /** The parsed command line. */
    record Options(List<Integer> sizes, BenchConfig config, String machine, String commit, Path out) {

        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (!OPTIONS.contains(args[i])) {
                    throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + args[i]);
                }
                values.put(args[i], args[i + 1]);
            }
            try {
                List<Integer> sizes = Arrays.stream(values.getOrDefault("--sizes", "10000,100000,1000000")
                        .split(",")).map(String::trim).map(Integer::parseInt).toList();
                if (sizes.isEmpty() || sizes.stream().anyMatch(size -> size < 1)) {
                    throw new IllegalArgumentException("Every size must be at least 1.");
                }
                BenchConfig config = new BenchConfig(Integer.parseInt(values.getOrDefault("--repeats", "7")),
                        Integer.parseInt(values.getOrDefault("--warmup", "3")),
                        Long.parseLong(values.getOrDefault("--seed", "42")));
                return new Options(sizes, config, values.getOrDefault("--machine", "unknown"),
                        values.getOrDefault("--commit", "unknown"),
                        Path.of(values.getOrDefault("--out", "../docs/benchmarks/results.json")));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Sizes, repeats, warmup and seed must be whole numbers.", e);
            }
        }
    }
}
