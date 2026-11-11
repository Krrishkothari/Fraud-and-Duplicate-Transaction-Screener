package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.txnscreen.algo.bloom.BloomMath;
import com.txnscreen.api.ApiException;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;
import com.txnscreen.ingest.BlacklistParser;
import com.txnscreen.ingest.CsvTransactionParser;

class ScreeningServiceTest {

    private static final Path SAMPLES = Path.of("..", "data", "samples");

    private final ScreeningService service = new ScreeningService();

    // Proves screening the 10,000 row sample flags exactly the rows its ground truth labels mark.
    @Test
    void sampleFlagsMatchLabels() throws IOException {
        List<Transaction> transactions;
        Set<String> blacklist;
        try (InputStream rows = Files.newInputStream(SAMPLES.resolve("transactions_10000.csv"));
                InputStream accounts = Files.newInputStream(SAMPLES.resolve("blacklist_10000.csv"))) {
            transactions = new CsvTransactionParser().parse(rows).valid();
            blacklist = new BlacklistParser().parse(accounts);
        }

        ScreeningResult result = service.screenAll(transactions, blacklist, RunConfig.defaults());
        List<String> labels = Files.readAllLines(SAMPLES.resolve("labels_10000.csv"));

        assertAll(
                () -> assertEquals(labelledLines(labels, 2), flaggedLines(result, FlagType.DUPLICATE)),
                () -> assertEquals(labelledLines(labels, 3), flaggedLines(result, FlagType.BLACKLISTED)),
                () -> assertEquals(List.of(), result.warnings()));
    }

    // Proves an unset expected insertion count sizes the duplicate filter for the valid row count.
    @Test
    void expectedInsertionsDefaultsToRowCount() {
        List<Transaction> transactions = List.of(txn("T1", 2), txn("T2", 3), txn("T3", 4));

        ScreeningResult result = service.screenAll(transactions, Set.of("ACC9"), RunConfig.defaults());

        assertEquals(BloomMath.optimalBits(3, 0.01), result.duplicateStats().bitSize());
    }

    // Proves an empty blacklist and a saturated filter are both reported, with a suggested size.
    @Test
    void emptyBlacklistAndSaturationAreWarned() {
        List<Transaction> transactions = List.of(txn("T1", 2), txn("T2", 3), txn("T3", 4), txn("T4", 5));
        RunConfig config = new RunConfig(1, null, null, null, null, null);

        ScreeningResult result = service.screenAll(transactions, Set.of(), config);

        assertAll(
                () -> assertEquals(2, result.warnings().size()),
                () -> assertTrue(result.warnings().get(0).startsWith("The blacklist is empty")),
                () -> assertEquals("The duplicate filter is saturated after 4 insertions, so its false positive "
                        + "rate is above target. Use at least " + BloomMath.optimalBits(4, 0.01) + " bits.",
                        result.warnings().get(1)));
    }

    // Proves settings that would exceed the filter size cap are refused as an invalid config.
    @Test
    void oversizedFilterIsInvalidConfig() {
        RunConfig config = new RunConfig(100_000_000, null, null, null, null, null);

        ApiException error = assertThrows(ApiException.class,
                () -> service.screenAll(List.of(txn("T1", 2)), Set.of(), config));

        assertAll(
                () -> assertEquals("INVALID_CONFIG", error.getCode()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, error.getStatus()));
    }

    private static Set<Integer> flaggedLines(ScreeningResult result, FlagType type) {
        Set<Integer> lines = new TreeSet<>();
        for (Flag flag : result.flags()) {
            if (flag.type() == type) {
                lines.add(flag.lineNumber());
            }
        }
        return lines;
    }

    private static Set<Integer> labelledLines(List<String> labels, int column) {
        Set<Integer> lines = new TreeSet<>();
        for (String label : labels.subList(1, labels.size())) {
            String[] fields = label.split(",");
            if (Boolean.parseBoolean(fields[column])) {
                lines.add(Integer.parseInt(fields[0]));
            }
        }
        return lines;
    }

    private static Transaction txn(String transactionId, int lineNumber) {
        return new Transaction(lineNumber, transactionId, "ACC1", 100, 1_767_225_600L + lineNumber, "Shop");
    }
}
