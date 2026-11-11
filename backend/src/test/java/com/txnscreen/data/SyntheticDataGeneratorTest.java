package com.txnscreen.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.txnscreen.domain.Transaction;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyntheticDataGeneratorTest {

    // Proves generation is reproducible: the same seed and rates give identical data.
    @Test
    void sameSeedGivesIdenticalOutput() {
        GeneratedData first = SyntheticDataGenerator.generate(5_000, 42, 0.05, 0.1);
        GeneratedData second = SyntheticDataGenerator.generate(5_000, 42, 0.05, 0.1);

        assertEquals(first, second);
    }

    // Proves the row count is exactly the requested size, with a label and a line number for every row.
    @Test
    void sizesAreExact() {
        for (int size : new int[] {1, 2, 50, 1_000, 10_000}) {
            GeneratedData data = SyntheticDataGenerator.generate(size, 7, 0.1, 0.1);

            assertEquals(size, data.rows().size(), "rows for size " + size);
            assertEquals(size, data.labels().size(), "labels for size " + size);
            assertEquals(2, data.rows().get(0).lineNumber());
            assertEquals(size + 1, data.rows().get(size - 1).lineNumber());
        }
    }

    // Proves the share of duplicate rows is close to the requested rate.
    @Test
    void duplicateCountIsCloseToTheRate() {
        int size = 20_000;
        double rate = 0.05;
        GeneratedData data = SyntheticDataGenerator.generate(size, 42, rate, 0.05);

        long duplicates = data.labels().values().stream().filter(Label::duplicate).count();

        double expected = size * rate;
        assertTrue(Math.abs(duplicates - expected) < expected * 0.1,
                "expected about " + expected + " duplicates, got " + duplicates);
    }

    // Proves the labels are true: a labelled duplicate repeats an earlier row's ID and fields, every
    // other row has a new ID, and the blacklisted label matches the blacklist.
    @Test
    void labelsMatchTheRows() {
        GeneratedData data = SyntheticDataGenerator.generate(10_000, 42, 0.05, 0.05);
        Set<String> seenIds = new HashSet<>();
        long previousTime = Long.MIN_VALUE;

        for (Transaction row : data.rows()) {
            Label label = data.labels().get(row.lineNumber());

            assertEquals(label.duplicate(), seenIds.contains(row.transactionId()), "line " + row.lineNumber());
            assertEquals(label.blacklisted(), data.blacklist().contains(row.accountId()),
                    "line " + row.lineNumber());
            assertTrue(row.epochSeconds() >= previousTime, "rows are sorted by time");
            assertTrue(row.amountPaise() >= 100, "amount is at least 1.00");
            seenIds.add(row.transactionId());
            previousTime = row.epochSeconds();
        }
        Set<String> accountsInData = new HashSet<>();
        data.rows().forEach(row -> accountsInData.add(row.accountId()));
        assertFalse(accountsInData.containsAll(data.blacklist()),
                "the blacklist also holds accounts that never appear in the data");
    }
}
