package com.txnscreen.data;

import com.txnscreen.domain.Money;
import com.txnscreen.domain.Transaction;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Writes generated data as the CSV files the app reads. Lines always end in "\n" so the same
 * data gives byte-identical files on every operating system.
 */
public final class SampleCsvWriter {

    static final String TRANSACTIONS_HEADER = "transaction_id,account_id,amount,timestamp,merchant";
    static final String BLACKLIST_HEADER = "account_id";
    static final String LABELS_HEADER = "line_number,transaction_id,duplicate,blacklisted";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

    private SampleCsvWriter() {
    }

    /**
     * Writes the rows with the standard header, amounts as rupees with 2 decimals and
     * timestamps as ISO-8601 with the +05:30 offset.
     *
     * @param file the file to create or replace
     * @param rows the rows in file order
     * @throws IOException if the file cannot be written
     */
    public static void writeTransactions(Path file, List<Transaction> rows) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeLine(out, TRANSACTIONS_HEADER);
            for (Transaction t : rows) {
                writeLine(out, String.join(",", t.transactionId(), t.accountId(), Money.format(t.amountPaise()),
                        formatTimestamp(t.epochSeconds()), t.merchant()));
            }
        }
    }

    /**
     * Writes one account ID per line under the header account_id, in sorted order.
     *
     * @param file the file to create or replace
     * @param blacklist the blacklisted account IDs
     * @throws IOException if the file cannot be written
     */
    public static void writeBlacklist(Path file, SortedSet<String> blacklist) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeLine(out, BLACKLIST_HEADER);
            for (String accountId : blacklist) {
                writeLine(out, accountId);
            }
        }
    }

    /**
     * Writes the ground truth for each row: its CSV line number, transaction ID, and whether it
     * is a duplicate and whether its account is blacklisted.
     *
     * @param file the file to create or replace
     * @param rows the rows in file order
     * @param labels the label for each row, keyed by line number
     * @throws IOException if the file cannot be written
     */
    public static void writeLabels(Path file, List<Transaction> rows, Map<Integer, Label> labels)
            throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeLine(out, LABELS_HEADER);
            for (Transaction t : rows) {
                Label label = labels.get(t.lineNumber());
                writeLine(out, t.lineNumber() + "," + t.transactionId() + "," + label.duplicate() + ","
                        + label.blacklisted());
            }
        }
    }

    static String formatTimestamp(long epochSeconds) {
        return TIMESTAMP.format(Instant.ofEpochSecond(epochSeconds).atOffset(SyntheticDataGenerator.OFFSET));
    }

    private static void writeLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write('\n');
    }
}
