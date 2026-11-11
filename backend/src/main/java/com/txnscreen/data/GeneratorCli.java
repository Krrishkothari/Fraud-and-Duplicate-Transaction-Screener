package com.txnscreen.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Command line entry point that writes transactions_SIZE.csv, blacklist_SIZE.csv and
 * labels_SIZE.csv. Run it from backend/ after compiling:
 *
 * <pre>
 * ./mvnw -q compile
 * java -cp target/classes com.txnscreen.data.GeneratorCli --size 100000 --seed 42 \
 *     --duplicate-rate 0.02 --blacklist-rate 0.05 --out-dir ../data/generated
 * </pre>
 *
 * <p>Every option is optional; the defaults are shown above, except size, which defaults to 1000.
 */
public final class GeneratorCli {

    private static final Set<String> OPTIONS =
            Set.of("--size", "--seed", "--duplicate-rate", "--blacklist-rate", "--out-dir");

    private GeneratorCli() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: GeneratorCli [--size N] [--seed N] [--duplicate-rate R] "
                    + "[--blacklist-rate R] [--out-dir DIR]");
            System.exit(2);
            return;
        }

        int size = Integer.parseInt(options.getOrDefault("--size", "1000"));
        long seed = Long.parseLong(options.getOrDefault("--seed", "42"));
        double duplicateRate = Double.parseDouble(options.getOrDefault("--duplicate-rate", "0.02"));
        double blacklistRate = Double.parseDouble(options.getOrDefault("--blacklist-rate", "0.05"));
        Path outDir = Path.of(options.getOrDefault("--out-dir", "../data/generated"));

        GeneratedData data = SyntheticDataGenerator.generate(size, seed, duplicateRate, blacklistRate);
        Files.createDirectories(outDir);
        SampleCsvWriter.writeTransactions(outDir.resolve("transactions_" + size + ".csv"), data.rows());
        SampleCsvWriter.writeBlacklist(outDir.resolve("blacklist_" + size + ".csv"), data.blacklist());
        SampleCsvWriter.writeLabels(outDir.resolve("labels_" + size + ".csv"), data.rows(), data.labels());

        long duplicates = data.labels().values().stream().filter(Label::duplicate).count();
        long blacklistedRows = data.labels().values().stream().filter(Label::blacklisted).count();
        System.out.printf("Wrote %d rows (%d duplicates, %d on blacklisted accounts) and %d blacklisted "
                + "accounts to %s%n", size, duplicates, blacklistedRows, data.blacklist().size(),
                outDir.toAbsolutePath().normalize());
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!OPTIONS.contains(args[i])) {
                throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for " + args[i]);
            }
            options.put(args[i], args[i + 1]);
        }
        return options;
    }
}
