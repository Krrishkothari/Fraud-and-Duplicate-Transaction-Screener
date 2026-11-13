package com.txnscreen.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.txnscreen.algo.segtree.TimeWindowIndex;
import com.txnscreen.algo.segtree.WindowResult;
import com.txnscreen.algo.select.RandomizedQuickselect;
import com.txnscreen.algo.select.SelectResult;
import com.txnscreen.api.ApiException;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.Money;
import com.txnscreen.domain.Transaction;

/**
 * Sets the risk threshold: a percentile of the transaction amounts, found with randomized Quickselect.
 * Amounts strictly above a run's threshold are flagged HIGH_AMOUNT.
 */
@Service
public class ThresholdService {

    /**
     * Finds a percentile of the amounts and counts the amounts strictly above it. Only the Quickselect step
     * is timed.
     *
     * <p>Time complexity: expected O(n). Space complexity: O(n) for Quickselect's copy.
     *
     * @param amounts the amounts in paise, at least one; not modified
     * @param percentile the percentile, in (0, 100]
     * @param seed the seed for Quickselect's pivot choices
     * @return the threshold and how it was found
     * @throws IllegalArgumentException if amounts is empty or the percentile is outside (0, 100]
     */
    public ThresholdResult compute(long[] amounts, double percentile, long seed) {
        long start = System.nanoTime();
        SelectResult selected = RandomizedQuickselect.selectPercentile(amounts, percentile, seed);
        long algoMicros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);

        long countAbove = 0;
        for (long amount : amounts) {
            if (amount > selected.value()) {
                countAbove++;
            }
        }
        return new ThresholdResult(percentile, selected.value(), amounts.length, countAbove,
                selected.comparisons(), algoMicros);
    }

    /**
     * Finds a percentile of the amounts in one time window of a run. Missing bounds default to the start or
     * end of the run's data, and the window is checked and clamped exactly as the analytics endpoints do.
     * The window's transactions are found by binary search, since the run's transactions are sorted by time.
     *
     * <p>Time complexity: O(log n) to find the window, then expected O(w) for its w amounts. Space
     * complexity: O(w).
     *
     * @param run the run
     * @param percentile the percentile, in (0, 100]
     * @param fromEpoch the window start in epoch seconds, or null for the data start
     * @param toEpoch the window end in epoch seconds, or null for the data end
     * @return the threshold with the window bounds actually used
     * @throws ApiException 400 INVALID_WINDOW or 400 WINDOW_OUT_OF_RANGE as for a window query, or 422
     *         EMPTY_WINDOW if no transaction falls inside the window
     */
    public WindowThreshold forWindow(Run run, double percentile, Long fromEpoch, Long toEpoch) {
        WindowResult window;
        long[] amounts;
        synchronized (run.lock()) {
            TimeWindowIndex index = run.analyticsIndex();
            window = index.query(fromEpoch == null ? index.dataStart() : fromEpoch,
                    toEpoch == null ? index.dataEnd() : toEpoch);
            List<Transaction> transactions = run.transactions();
            int first = firstAtOrAfter(transactions, window.effectiveFrom());
            int end = firstAtOrAfter(transactions, window.effectiveTo() + 1);
            amounts = amounts(transactions.subList(first, end));
        }
        if (amounts.length == 0) {
            throw new ApiException("EMPTY_WINDOW", HttpStatus.UNPROCESSABLE_CONTENT,
                    "No transactions fall inside this window.",
                    Map.of("effectiveFrom", window.effectiveFrom(), "effectiveTo", window.effectiveTo()));
        }
        return new WindowThreshold(compute(amounts, percentile, run.config().seed()), window.effectiveFrom(),
                window.effectiveTo(), window.clamped());
    }

    /**
     * Adds a HIGH_AMOUNT flag for every transaction strictly above the threshold, keeping all flags in
     * transaction order: each transaction's screening flags come first, then its HIGH_AMOUNT flag.
     *
     * <p>Time complexity: O(n + f) for n transactions and f screening flags. Space complexity: O(n + f).
     *
     * @param transactions the transactions in screening order
     * @param screeningFlags the flags screening raised, in the same transaction order
     * @param threshold the run's threshold
     * @return every flag in transaction order
     */
    public List<Flag> withHighAmountFlags(List<Transaction> transactions, List<Flag> screeningFlags,
            ThresholdResult threshold) {
        String detail = highAmountDetail(threshold);
        List<Flag> flags = new ArrayList<>(screeningFlags.size() + (int) threshold.countAbove());
        int next = 0;
        for (Transaction t : transactions) {
            while (next < screeningFlags.size() && screeningFlags.get(next).lineNumber() == t.lineNumber()) {
                flags.add(screeningFlags.get(next++));
            }
            if (t.amountPaise() > threshold.valuePaise()) {
                flags.add(new Flag(t.transactionId(), t.lineNumber(), FlagType.HIGH_AMOUNT, detail));
            }
        }
        return flags;
    }

    /**
     * Returns the HIGH_AMOUNT flag for one transaction if its amount is strictly above the threshold.
     *
     * <p>Time complexity: O(1). Space complexity: O(1).
     *
     * @param transaction the transaction
     * @param threshold the run's threshold
     * @return the flag, or empty if the amount is at or below the threshold
     */
    public Optional<Flag> highAmountFlag(Transaction transaction, ThresholdResult threshold) {
        if (transaction.amountPaise() <= threshold.valuePaise()) {
            return Optional.empty();
        }
        return Optional.of(new Flag(transaction.transactionId(), transaction.lineNumber(), FlagType.HIGH_AMOUNT,
                highAmountDetail(threshold)));
    }

    private static String highAmountDetail(ThresholdResult threshold) {
        return "Above P" + percentileText(threshold) + " threshold of " + Money.format(threshold.valuePaise());
    }

    /**
     * Returns the threshold's percentile as plain text without trailing zeros, such as 99 or 99.9.
     *
     * @param threshold the threshold
     * @return the percentile text
     */
    static String percentileText(ThresholdResult threshold) {
        return BigDecimal.valueOf(threshold.percentile()).stripTrailingZeros().toPlainString();
    }

    /**
     * Copies the amounts of the transactions, in order.
     *
     * @param transactions the transactions
     * @return their amounts in paise
     */
    static long[] amounts(List<Transaction> transactions) {
        long[] amounts = new long[transactions.size()];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = transactions.get(i).amountPaise();
        }
        return amounts;
    }

    /** Returns the first index whose time is at or after epochSeconds, or the size if there is none. */
    private static int firstAtOrAfter(List<Transaction> sortedByTime, long epochSeconds) {
        int lo = 0;
        int hi = sortedByTime.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (sortedByTime.get(mid).epochSeconds() < epochSeconds) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
