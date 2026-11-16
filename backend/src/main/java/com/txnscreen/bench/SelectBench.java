package com.txnscreen.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.txnscreen.algo.select.RandomizedQuickselect;
import com.txnscreen.algo.select.SelectResult;
import com.txnscreen.baseline.SortPercentile;
import com.txnscreen.data.SyntheticDataGenerator;
import com.txnscreen.domain.Transaction;

/**
 * Compares the P95 and P99 amount found by our {@link RandomizedQuickselect} against the {@link SortPercentile}
 * baseline, on the amounts of n rows from the {@link SyntheticDataGenerator} with the run's seed. For each
 * percentile it measures both times, Quickselect's element comparisons, and comparisons divided by n, each
 * as the median, minimum and maximum over 25 pivot seeds. If Quickselect runs in expected O(n), the median
 * comparisons / n stays roughly constant as n grows.
 *
 * <p>It also runs one adversarial case at P99: every amount equal. A two-way partition would put all the
 * equal values on one side and degrade to O(n^2); the three-way partition puts them in the equal block and
 * finishes after one pass, so comparisons / n stays near 2.
 *
 * <p>Both methods must return the same value for every input, and the benchmark throws before writing any
 * result if they do not.
 */
public final class SelectBench implements Benchmark {

    /** The percentiles measured on the generated amounts. */
    static final double[] PERCENTILES = {95, 99};

    /** The percentile measured on the all-equal input. */
    static final double ADVERSARIAL_PERCENTILE = 99;

    /** The amount every value has in the all-equal input, 500.00 rupees. */
    static final long EQUAL_AMOUNT_PAISE = 50_000;

    /**
     * The number of pivot seeds comparisons are counted with. Their median, minimum and maximum are recorded,
     * with the seed count as the repeats.
     */
    static final int COMPARISON_SEEDS = 25;

    private static final String NAME = "selection";
    private static final String QUICKSELECT = "quickselect";
    private static final String SORT = "sort";
    private static final double DUPLICATE_RATE = 0.02;
    private static final double BLACKLIST_RATE = 0.05;
    private static final double NANOS_PER_MILLI = 1_000_000;

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Measures Quickselect and the sort baseline on size generated amounts, then on size equal amounts.
     *
     * <p>Time complexity: O(n log n) to generate the rows, then expected O((warmup + repeats) * n) for
     * Quickselect and O((warmup + repeats) * n log n) for the sorts, for each of the 3 cases. Space
     * complexity: O(n).
     *
     * @param size the number of amounts n
     * @param cfg the repeats, warmup iterations and the seed, used for the data and Quickselect's pivots
     * @return the measurements for both variants
     * @throws IllegalStateException if the two methods return different values for any case
     */
    @Override
    public List<Measurement> run(int size, BenchConfig cfg) {
        long[] amounts = amounts(
                SyntheticDataGenerator.generate(size, cfg.seed(), DUPLICATE_RATE, BLACKLIST_RATE).rows());
        List<Measurement> measurements = new ArrayList<>();
        for (double p : PERCENTILES) {
            measurements.addAll(measure(amounts, p, "P" + (int) p, size, cfg));
        }
        long[] allEqual = new long[size];
        Arrays.fill(allEqual, EQUAL_AMOUNT_PAISE);
        measurements.addAll(measure(allEqual, ADVERSARIAL_PERCENTILE, "AllEqualP99", size, cfg));
        return List.copyOf(measurements);
    }

    /**
     * Times both methods at one percentile and records Quickselect's comparison count.
     *
     * @param values the amounts
     * @param p the percentile
     * @param label the metric suffix, such as "P95" or "AllEqualP99"
     * @param size the input size
     * @param cfg the repeats, warmup iterations and the seed
     * @return the measurements for this case
     */
    private static List<Measurement> measure(long[] values, double p, String label, int size, BenchConfig cfg) {
        SelectResult selected = RandomizedQuickselect.selectPercentile(values, p, cfg.seed());
        long sorted = SortPercentile.percentile(values, p);
        if (selected.value() != sorted) {
            throw new IllegalStateException(label + " at size " + size + " gave " + selected.value()
                    + " from Quickselect but " + sorted + " from sorting.");
        }
        Timer.Timing quickselect = Timer.time(cfg, () -> RandomizedQuickselect.percentile(values, p, cfg.seed()));
        Timer.Timing sort = Timer.time(cfg, () -> SortPercentile.percentile(values, p));
        long[] comparisons = comparisonsOverSeeds(values, p, cfg.seed());
        Timer.Timing spread = Timer.Timing.of(comparisons);
        return List.of(
                quickselect.toMeasurement(NAME, QUICKSELECT, size, "time" + label, "ms", NANOS_PER_MILLI),
                spread.toMeasurement(NAME, QUICKSELECT, size, "comparisons" + label, "count", 1),
                spread.toMeasurement(NAME, QUICKSELECT, size, "comparisonsPerN" + label, "ratio", size),
                // The check above has just confirmed both methods return this value.
                Measurement.single(NAME, QUICKSELECT, size, "value" + label, "paise", selected.value()),
                sort.toMeasurement(NAME, SORT, size, "time" + label, "ms", NANOS_PER_MILLI));
    }

    /**
     * Counts Quickselect's comparisons with COMPARISON_SEEDS different pivot seeds, starting at seed. One run
     * shows the cost of one random pivot sequence; many show the expected cost and how far a run can stray.
     *
     * <p>Time complexity: expected O(S * n) for S seeds. Space complexity: O(n + S).
     */
    private static long[] comparisonsOverSeeds(long[] values, double p, long seed) {
        long[] comparisons = new long[COMPARISON_SEEDS];
        for (int i = 0; i < COMPARISON_SEEDS; i++) {
            comparisons[i] = RandomizedQuickselect.selectPercentile(values, p, seed + i).comparisons();
        }
        return comparisons;
    }

    private static long[] amounts(List<Transaction> rows) {
        long[] amounts = new long[rows.size()];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = rows.get(i).amountPaise();
        }
        return amounts;
    }
}
