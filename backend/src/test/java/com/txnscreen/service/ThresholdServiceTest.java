package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.txnscreen.data.SyntheticDataGenerator;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Transaction;

class ThresholdServiceTest {

    private final ThresholdService service = new ThresholdService();

    // Proves the P99 threshold on the committed 10K sample (seed 42) equals the nearest-rank value from sorting.
    @Test
    void p99OnTenThousandSampleEqualsSortedValue() {
        long[] amounts = ThresholdService.amounts(SyntheticDataGenerator.generate(10_000, 42, 0.02, 0.05).rows());
        long[] sorted = amounts.clone();
        Arrays.sort(sorted);
        long expected = sorted[(int) Math.ceil(0.99 * amounts.length) - 1];

        ThresholdResult result = service.compute(amounts, 99, 42);

        assertEquals(expected, result.valuePaise());
        assertEquals(10_000, result.n());
        assertEquals(Arrays.stream(amounts).filter(a -> a > expected).count(), result.countAbove());
    }

    // Proves only amounts strictly above the threshold are counted, so ties with it are not.
    @Test
    void countAboveIsStrict() {
        ThresholdResult result = service.compute(new long[] {100, 200, 200, 300}, 75, 42);

        assertEquals(200, result.valuePaise());
        assertEquals(1, result.countAbove());
    }

    // Proves HIGH_AMOUNT flags go to amounts above the threshold, after each row's screening flags.
    @Test
    void highAmountFlagsKeepTransactionOrder() {
        List<Transaction> transactions = List.of(
                new Transaction(2, "T1", "ACC1", 100, 1_000, "Shop"),
                new Transaction(3, "T2", "ACC2", 500, 1_060, "Shop"),
                new Transaction(4, "T3", "ACC3", 900, 1_120, "Shop"));
        Flag duplicate = new Flag("T2", 3, FlagType.DUPLICATE, "Duplicate of line 2");
        ThresholdResult threshold = new ThresholdResult(99, 400, 3, 2, 0, 0);

        List<Flag> flags = service.withHighAmountFlags(transactions, List.of(duplicate), threshold);

        assertEquals(List.of(
                duplicate,
                new Flag("T2", 3, FlagType.HIGH_AMOUNT, "Above P99 threshold of 4.00"),
                new Flag("T3", 4, FlagType.HIGH_AMOUNT, "Above P99 threshold of 4.00")), flags);
    }
}
