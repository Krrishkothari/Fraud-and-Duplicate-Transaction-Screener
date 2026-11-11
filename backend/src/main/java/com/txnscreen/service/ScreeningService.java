package com.txnscreen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.txnscreen.api.ApiException;
import com.txnscreen.api.FieldViolation;
import com.txnscreen.domain.Flag;
import com.txnscreen.domain.Transaction;

/**
 * Screens parsed transactions against a blacklist and for repeated transaction IDs.
 */
@Service
public class ScreeningService {

    /**
     * Screens every transaction in the given order, which should be time order.
     *
     * <p>Time complexity: O(t * k + b + m / 64) for t transactions, b blacklisted accounts and m total filter
     * bits, after hashing each key. Space complexity: O(t + b + m) bits.
     *
     * @param transactions the valid transactions, sorted by time
     * @param blacklist the blacklisted account IDs, which may be empty
     * @param config the run settings
     * @return the flags, the stats of both filters and any warnings
     * @throws ApiException 400 INVALID_CONFIG if the settings would make a filter larger than the cap
     */
    public ScreeningResult screenAll(List<Transaction> transactions, Set<String> blacklist, RunConfig config) {
        long expectedInsertions = config.expectedInsertions() == null
                ? Math.max(1, transactions.size())
                : config.expectedInsertions();
        Screener screener = newScreener(blacklist, expectedInsertions, config);

        List<Flag> flags = new ArrayList<>();
        for (Transaction transaction : transactions) {
            flags.addAll(screener.screenOne(transaction));
        }

        FilterStats blacklistStats = screener.blacklistStats();
        FilterStats duplicateStats = screener.duplicateStats();
        List<String> warnings = new ArrayList<>();
        if (screener.blacklistSize() == 0) {
            warnings.add("The blacklist is empty, so no transaction was checked against blacklisted accounts.");
        }
        addSaturationWarning(warnings, "blacklist", blacklistStats);
        addSaturationWarning(warnings, "duplicate", duplicateStats);
        return new ScreeningResult(screener, flags, blacklistStats, duplicateStats, warnings);
    }

    private static Screener newScreener(Set<String> blacklist, long expectedInsertions, RunConfig config) {
        try {
            return new Screener(blacklist, expectedInsertions, config);
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_CONFIG", HttpStatus.BAD_REQUEST, "The configuration is invalid.",
                    Map.of("fieldErrors", List.of(new FieldViolation("config", e.getMessage()))));
        }
    }

    private static void addSaturationWarning(List<String> warnings, String filterName, FilterStats stats) {
        if (stats.saturated()) {
            warnings.add("The " + filterName + " filter is saturated after " + stats.inserted()
                    + " insertions, so its false positive rate is above target. Use at least "
                    + stats.suggestedBits() + " bits.");
        }
    }
}
